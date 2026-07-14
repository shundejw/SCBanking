package com.scb.trade.lcdocchecker.checks;

import com.scb.trade.lcdocchecker.config.RulesProperties;
import com.scb.trade.lcdocchecker.domain.CheckResult;
import com.scb.trade.lcdocchecker.domain.CheckStatus;
import com.scb.trade.lcdocchecker.domain.Discrepancy;
import com.scb.trade.lcdocchecker.domain.DocumentType;
import com.scb.trade.lcdocchecker.domain.ExtractedDocument;
import com.scb.trade.lcdocchecker.domain.LcTerms;
import com.scb.trade.lcdocchecker.util.FlowLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Runs the registered {@link DocumentCheck} beans in their deterministic Spring {@code @Order},
 * filtered by document-type applicability ({@link DocumentCheck#appliesTo()}) and the
 * {@code lcchecker.rules} allowlist.
 *
 * <p>A check that throws degrades to {@link CheckStatus#UNABLE} rather than crashing the run
 * (no silent pass, but loud downgrade).
 */
@Service
public class CheckEngineService {

    private static final Logger log = LoggerFactory.getLogger(CheckEngineService.class);

    private final List<DocumentCheck<?>> checks;
    private final RulesProperties rulesProperties;

    @Autowired
    public CheckEngineService(List<DocumentCheck<?>> checks, RulesProperties rulesProperties) {
        this.checks = checks;
        this.rulesProperties = rulesProperties;
    }

    /** Convenience constructor: ALL registered checks active (no allowlist). Test-friendly. */
    public CheckEngineService(List<DocumentCheck<?>> checks) {
        this(checks, null);
    }

    public List<CheckResult> run(String runId, LcTerms lc, ExtractedDocument doc) {
        long startNs = System.nanoTime();
        DocumentType type = doc.documentType();
        Set<String> enabled = rulesProperties == null ? null : rulesProperties.resolve(type);
        List<DocumentCheck<?>> active = checks.stream()
                .filter(c -> c.appliesTo().contains(type))
                .filter(c -> enabled == null || enabled.contains(c.checkId()))
                .toList();
        FlowLog.info(log, CheckEngineService.class, "run",
                "stage", "START",
                "runId", runId,
                "documentType", type,
                "checkCount", active.size(),
                "input.lc", FlowLog.prettyValue(lc),
                "input.document", FlowLog.prettyValue(doc));

        List<CheckResult> results = active.stream()
                .map(c -> executeSafely(runId, c, lc, doc))
                .toList();

        long pass = results.stream().filter(r -> r.status() == CheckStatus.PASS).count();
        long fail = results.stream().filter(r -> r.status() == CheckStatus.FAIL).count();
        long unable = results.stream().filter(r -> r.status() == CheckStatus.UNABLE).count();
        long na = results.stream().filter(r -> r.status() == CheckStatus.NOT_APPLICABLE).count();
        FlowLog.info(log, CheckEngineService.class, "run",
                "stage", "END",
                "runId", runId,
                "documentType", type,
                "result", fail > 0 ? "NON_COMPLIANT" : "COMPLIANT",
                "pass", pass,
                "fail", fail,
                "unable", unable,
                "notApplicable", na,
                "output.results", FlowLog.prettyValue(results),
                "costMs", elapsedMs(startNs));
        return results;
    }

    /** Backward-compatible overload for tests that do not supply a runId. */
    public List<CheckResult> run(LcTerms lc, ExtractedDocument doc) {
        return run("unknown", lc, doc);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private CheckResult executeSafely(String runId, DocumentCheck check, LcTerms lc, ExtractedDocument doc) {
        FlowLog.info(log, CheckEngineService.class, "executeSafely",
                "stage", "STEP",
                "runId", runId,
                "step", check.checkId(),
                "input", FlowLog.prettyValue(stepInputSummary(lc, doc)));
        try {
            CheckResult result = check.execute(lc, doc);
            logCheckResult(runId, check.checkId(), result);
            return result;
        } catch (Exception e) {
            FlowLog.warn(log, CheckEngineService.class, "executeSafely",
                    "stage", "STEP",
                    "runId", runId,
                    "step", check.checkId(),
                    "result", "UNABLE",
                    "errorMessage", FlowLog.prettyValue(e.getMessage()));
            return CheckResult.unable(check.checkId(),
                    "Check '" + check.checkId() + "' could not be completed: " + e.getMessage());
        }
    }

    private void logCheckResult(String runId, String checkId, CheckResult result) {
        if (result.status() == CheckStatus.FAIL) {
            Discrepancy d = result.discrepancy();
            FlowLog.warn(log, CheckEngineService.class, "executeSafely",
                    "stage", "STEP",
                    "runId", runId,
                    "step", checkId,
                    "result", "FAIL",
                    "output", FlowLog.prettyValue(result),
                    "field", d == null ? "unknown" : d.field(),
                    "reason", d == null ? "discrepancy" : d.description());
            return;
        }
        if (result.status() == CheckStatus.UNABLE) {
            FlowLog.warn(log, CheckEngineService.class, "executeSafely",
                    "stage", "STEP",
                    "runId", runId,
                    "step", checkId,
                    "result", "UNABLE",
                    "output", FlowLog.prettyValue(result),
                    "reason", result.message());
            return;
        }
        FlowLog.info(log, CheckEngineService.class, "executeSafely",
                "stage", "STEP",
                "runId", runId,
                "step", checkId,
                "result", result.status().name(),
                "output", FlowLog.prettyValue(result));
    }

    private String stepInputSummary(LcTerms lc, ExtractedDocument doc) {
        return "LcTerms{"
                + "lcNumber=" + lc.lcNumber()
                + ", currency=" + lc.currency()
                + ", amount=" + lc.amount()
                + ", applicantName=" + lc.applicantName()
                + ", beneficiaryName=" + lc.beneficiaryName()
                + ", portOfLoading=" + lc.portOfLoading()
                + ", portOfDischarge=" + lc.portOfDischarge()
                + ", goodsDescription=" + lc.goodsDescription()
                + "}\n"
                + doc.getClass().getSimpleName() + "{"
                + "documentType=" + doc.documentType()
                + ", rawTextChars=" + (doc.rawText() == null ? 0 : doc.rawText().length())
                + "}";
    }

    private static long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }
}
