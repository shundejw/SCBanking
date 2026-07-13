package com.scb.trade.lcdocchecker.api;

import com.scb.trade.lcdocchecker.checks.CheckEngineService;
import com.scb.trade.lcdocchecker.domain.CheckReport;
import com.scb.trade.lcdocchecker.domain.InvoiceFields;
import com.scb.trade.lcdocchecker.domain.LcTerms;
import com.scb.trade.lcdocchecker.extractor.DocumentExtractorService;
import com.scb.trade.lcdocchecker.guard.UploadGuardService;
import com.scb.trade.lcdocchecker.parser.LcParserService;
import com.scb.trade.lcdocchecker.report.ReportAssemblerService;
import com.scb.trade.lcdocchecker.store.ArtifactStoreService;
import com.scb.trade.lcdocchecker.store.CheckRunStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

import com.scb.trade.lcdocchecker.domain.CheckResult;

/**
 * Coordinates the full pipeline for one check run and persists every intermediate artifact
 * for inspection:
 *
 * <pre>
 *   guard → parse LC → extract invoice → run checks → assemble report
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

    public CheckReport process(String runId, String lcText, byte[] pdfBytes) {
        log.info("Run {}: validating inputs", runId);
        guard.validateLcText(lcText);
        guard.validatePdf(pdfBytes);

        log.info("Run {}: parsing MT700", runId);
        LcTerms lc = parser.parse(lcText);            // InvalidMt700Exception → 422
        artifactStore.save(runId, "lc_parsed", lc);

        log.info("Run {}: extracting invoice", runId);
        InvoiceFields invoice = extractor.extract(pdfBytes);  // DocumentExtractionException → 422
        artifactStore.save(runId, "invoice_extracted", invoice);
        if (invoice.rawText() != null && !invoice.rawText().isBlank()) {
            artifactStore.save(runId, "pdf_text", invoice.rawText());
        }

        List<CheckResult> results = engine.run(lc, invoice);
        artifactStore.save(runId, "check_results", results);

        CheckReport report = assembler.assemble(results);
        artifactStore.save(runId, "final_report", report);
        runStore.put(runId, report);

        log.info("Run {}: complete — compliant={}, discrepancies={}",
                runId, report.compliant(), report.discrepancies().size());
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
}
