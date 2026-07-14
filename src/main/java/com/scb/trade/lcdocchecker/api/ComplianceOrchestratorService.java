package com.scb.trade.lcdocchecker.api;

import com.scb.trade.lcdocchecker.checks.CheckEngineService;
import com.scb.trade.lcdocchecker.domain.CheckReport;
import com.scb.trade.lcdocchecker.domain.CheckResult;
import com.scb.trade.lcdocchecker.domain.CheckStatus;
import com.scb.trade.lcdocchecker.domain.DocumentType;
import com.scb.trade.lcdocchecker.domain.ExtractedDocument;
import com.scb.trade.lcdocchecker.domain.LcTerms;
import com.scb.trade.lcdocchecker.extractor.DocumentExtractorService;
import com.scb.trade.lcdocchecker.guard.UploadGuardService;
import com.scb.trade.lcdocchecker.parser.LcParserService;
import com.scb.trade.lcdocchecker.report.ReportAssemblerService;
import com.scb.trade.lcdocchecker.store.ArtifactStoreService;
import com.scb.trade.lcdocchecker.store.CheckRunStore;
import com.scb.trade.lcdocchecker.util.FlowLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Coordinates the full pipeline for one check run and persists every intermediate artifact
 * for inspection:
 *
 * <pre>
 *   guard → parse LC → extract document → run checks → assemble report
 * </pre>
 *
 * Each stage is stored under {@code <rootDir>/<runId>/<stage>.json}.
 */
@Service
public class ComplianceOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceOrchestratorService.class);

    private final UploadGuardService guard;
    private final LcParserService parser;
    private final DocumentExtractorService extractor;
    private final CheckEngineService engine;
    private final ReportAssemblerService assembler;
    private final ArtifactStoreService artifactStore;
    private final CheckRunStore runStore;

    public ComplianceOrchestratorService(UploadGuardService guard,
                                         LcParserService parser,
                                         DocumentExtractorService extractor,
                                         CheckEngineService engine,
                                         ReportAssemblerService assembler,
                                         ArtifactStoreService artifactStore,
                                         CheckRunStore runStore) {
        this.guard = guard;
        this.parser = parser;
        this.extractor = extractor;
        this.engine = engine;
        this.assembler = assembler;
        this.artifactStore = artifactStore;
        this.runStore = runStore;
    }

    public CheckReport process(String runId, String lcText, byte[] pdfBytes, DocumentType documentType) {
        long startNs = System.nanoTime();
        FlowLog.info(log, ComplianceOrchestratorService.class, "process",
                "stage", "START", "runId", runId,
                "lcChars", lcText.length(), "pdfBytes", pdfBytes.length, "documentType", documentType);

        FlowLog.info(log, ComplianceOrchestratorService.class, "process",
                "stage", "STEP", "runId", runId, "step", "validateInputs");
        guard.validateLcText(lcText);
        guard.validatePdf(pdfBytes);

        FlowLog.info(log, ComplianceOrchestratorService.class, "process",
                "stage", "STEP", "runId", runId, "step", "parseMt700");
        LcTerms lc = parser.parse(lcText);
        artifactStore.save(runId, "lc_parsed", lc);

        FlowLog.info(log, ComplianceOrchestratorService.class, "process",
                "stage", "STEP", "runId", runId, "step", "extractDocument", "documentType", documentType);
        ExtractedDocument doc = extractor.extract(pdfBytes, documentType);
        artifactStore.save(runId, documentType.name().toLowerCase() + "_extracted", doc);
        if (doc.rawText() != null && !doc.rawText().isBlank()) {
            artifactStore.save(runId, "pdf_text", doc.rawText());
        }

        FlowLog.info(log, ComplianceOrchestratorService.class, "process",
                "stage", "STEP", "runId", runId, "step", "runChecks", "lcNumber", lc.lcNumber());
        List<CheckResult> results = engine.run(runId, lc, doc);
        artifactStore.save(runId, "check_results", results);

        FlowLog.info(log, ComplianceOrchestratorService.class, "process",
                "stage", "STEP", "runId", runId, "step", "assembleReport");
        CheckReport report = assembler.assemble(results);
        artifactStore.save(runId, "final_report", report);
        runStore.put(runId, report);

        long failCount = results.stream().filter(r -> r.status() == CheckStatus.FAIL).count();
        FlowLog.info(log, ComplianceOrchestratorService.class, "process",
                "stage", "END",
                "runId", runId,
                "result", report.compliant() ? "COMPLIANT" : "NON_COMPLIANT",
                "discrepancies", report.discrepancies().size(),
                "failedChecks", failCount,
                "costMs", elapsedMs(startNs));
        return report;
    }

    public CheckReport getReport(String runId) {
        return runStore.get(runId).orElse(null);
    }

    public String getArtifact(String runId, String stage) {
        return artifactStore.load(runId, stage);
    }

    public boolean runExists(String runId) {
        return runStore.get(runId).isPresent() || artifactStore.exists(runId);
    }

    private static long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }
}
