package com.scb.trade.lcdocchecker.api;

import com.scb.trade.lcdocchecker.domain.CheckReport;
import com.scb.trade.lcdocchecker.domain.DocumentType;
import com.scb.trade.lcdocchecker.exception.NotFoundException;
import com.scb.trade.lcdocchecker.util.FlowLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * REST API for LC ↔ invoice document checking.
 *
 * <ul>
 *   <li>{@code POST /checks} — multipart: {@code lc} (raw MT700 text) + {@code invoice} (PDF),
 *       with an optional {@code documentType} query param (default {@code INVOICE}).</li>
 *   <li>{@code GET /checks/{runId}} — final compliance report.</li>
 *   <li>{@code GET /checks/{runId}/artifacts/{stage}} — intermediate artifact JSON
 *       (lc_parsed, {@code <documentType>_extracted} e.g. invoice_extracted, pdf_text,
 *       check_results, final_report).</li>
 * </ul>
 */
@RestController
@RequestMapping("/checks")
public class CheckController {

    private static final Logger log = LoggerFactory.getLogger(CheckController.class);

    private final ComplianceOrchestratorService orchestrator;

    public CheckController(ComplianceOrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CheckReport> check(
            @RequestParam("lc") String lcText,
            @RequestPart("invoice") MultipartFile invoice,
            @RequestParam(name = "documentType", defaultValue = "INVOICE") DocumentType documentType) throws IOException {
        String runId = UUID.randomUUID().toString();
        String fileName = invoice.getOriginalFilename() == null ? "unknown.pdf" : invoice.getOriginalFilename();
        long startNs = System.nanoTime();
        FlowLog.info(log, CheckController.class, "check",
                "stage", "START",
                "runId", runId,
                "fileName", fileName,
                "lcChars", lcText == null ? 0 : lcText.length(),
                "pdfBytes", invoice.getSize(),
                "documentType", documentType);
        CheckReport report = orchestrator.process(runId, lcText, invoice.getBytes(), documentType);
        FlowLog.info(log, CheckController.class, "check",
                "stage", "END",
                "runId", runId,
                "result", report.compliant() ? "COMPLIANT" : "NON_COMPLIANT",
                "discrepancies", report.discrepancies().size(),
                "costMs", elapsedMs(startNs));
        return ResponseEntity.ok()
                .header("X-Check-Run-Id", runId)
                .body(report);
    }

    @GetMapping(value = "/{runId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CheckReport getReport(@PathVariable("runId") String runId) {
        FlowLog.info(log, CheckController.class, "getReport", "stage", "START", "runId", runId);
        CheckReport report = orchestrator.getReport(runId);
        if (report == null) {
            throw new NotFoundException("Check run '" + runId + "' not found.");
        }
        FlowLog.info(log, CheckController.class, "getReport",
                "stage", "END", "runId", runId, "result", report.compliant() ? "COMPLIANT" : "NON_COMPLIANT");
        return report;
    }

    @GetMapping(value = "/{runId}/artifacts/{stage}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getArtifact(@PathVariable("runId") String runId, @PathVariable("stage") String stage) {
        FlowLog.info(log, CheckController.class, "getArtifact",
                "stage", "START", "runId", runId, "artifactStage", stage);
        if (!orchestrator.runExists(runId)) {
            throw new NotFoundException("Check run '" + runId + "' not found.");
        }
        String json = orchestrator.getArtifact(runId, stage);
        if (json == null) {
            throw new NotFoundException("Artifact '" + stage + "' not found for run '" + runId + "'.");
        }
        FlowLog.info(log, CheckController.class, "getArtifact",
                "stage", "END", "runId", runId, "artifactStage", stage, "jsonChars", json.length());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
    }

    private static long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }
}
