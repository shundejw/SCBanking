package com.scb.trade.lcdocchecker.api;

import com.scb.trade.lcdocchecker.domain.CheckReport;
import com.scb.trade.lcdocchecker.exception.NotFoundException;
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
 *   <li>{@code POST /checks} — multipart: {@code lc} (raw MT700 text) + {@code invoice} (PDF).</li>
 *   <li>{@code GET /checks/{runId}} — final compliance report.</li>
 *   <li>{@code GET /checks/{runId}/artifacts/{stage}} — intermediate artifact JSON
 *       (lc_parsed, invoice_extracted, pdf_text, check_results, final_report).</li>
 * </ul>
 */
@RestController
@RequestMapping("/checks")
public class CheckController {

    private final ComplianceOrchestratorService orchestrator;

    public CheckController(ComplianceOrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CheckReport> check(
            @RequestParam("lc") String lcText,
            @RequestPart("invoice") MultipartFile invoice) throws IOException {
        String runId = UUID.randomUUID().toString();
        CheckReport report = orchestrator.process(runId, lcText, invoice.getBytes());
        return ResponseEntity.ok()
                .header("X-Check-Run-Id", runId)
                .body(report);
    }

    @GetMapping(value = "/{runId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public CheckReport getReport(@PathVariable("runId") String runId) {
        CheckReport report = orchestrator.getReport(runId);
        if (report == null) {
            throw new NotFoundException("Check run '" + runId + "' not found.");
        }
        return report;
    }

    @GetMapping(value = "/{runId}/artifacts/{stage}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getArtifact(@PathVariable("runId") String runId, @PathVariable("stage") String stage) {
        if (!orchestrator.runExists(runId)) {
            throw new NotFoundException("Check run '" + runId + "' not found.");
        }
        String json = orchestrator.getArtifact(runId, stage);
        if (json == null) {
            throw new NotFoundException("Artifact '" + stage + "' not found for run '" + runId + "'.");
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json);
    }
}
