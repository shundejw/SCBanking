package com.scb.trade.lcdocchecker.checks;

import com.scb.trade.lcdocchecker.config.RulesProperties;
import com.scb.trade.lcdocchecker.domain.CheckResult;
import com.scb.trade.lcdocchecker.domain.CheckStatus;
import com.scb.trade.lcdocchecker.domain.Discrepancy;
import com.scb.trade.lcdocchecker.domain.InvoiceFields;
import com.scb.trade.lcdocchecker.domain.LcTerms;
import com.scb.trade.lcdocchecker.util.FlowLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Runs the registered {@link DocumentCheck} beans in their deterministic Spring {@code @Order}
 * (amount, currency, issuer, signature, applicant, goods, port-of-loading, port-of-discharge,
 * lc-reference), filtered by the {@code lcchecker.rules.enabled} allowlist.
 *
 * <p>A check that throws degrades to {@link com.scb.trade.lcdocchecker.domain.CheckStatus#UNABLE}
 * rather than crashing the run (rules.md §5.1: no silent pass, but loud downgrade).
 */
@Service
public class CheckEngineService {

    private static final Logger log = LoggerFactory.getLogger(CheckEngineService.class);

    private final List<DocumentCheck> checks;
    private final Set<String> enabled;

    @Autowired
    public CheckEngineService(List<DocumentCheck> checks, RulesProperties rulesProperties) {
        this(checks,
                rulesProperties == null || rulesProperties.enabled() == null || rulesProperties.enabled().isEmpty()
                        ? null : Set.copyOf(rulesProperties.enabled()));
    }

    /** Convenience constructor: ALL registered checks active (no allowlist). */
    public CheckEngineService(List<DocumentCheck> checks) {
        this(checks, (Set<String>) null);
    }

    /** Test-friendly constructor: {@code enabled == null} means all checks active. */
    public CheckEngineService(List<DocumentCheck> checks, Set<String> enabled) {
        this.checks = checks;
        this.enabled = enabled;
    }

    public List<CheckResult> run(String runId, LcTerms lc, InvoiceFields invoice) {
        long startNs = System.nanoTime();
        List<DocumentCheck> active = checks.stream()
                .filter(c -> enabled == null || enabled.contains(c.checkId()))
                .toList();
        FlowLog.info(log, CheckEngineService.class, "run",
                "stage", "START", "runId", runId, "checkCount", active.size());

        List<CheckResult> results = active.stream()
                .map(c -> executeSafely(runId, c, lc, invoice))
                .toList();

        long pass = results.stream().filter(r -> r.status() == CheckStatus.PASS).count();
        long fail = results.stream().filter(r -> r.status() == CheckStatus.FAIL).count();
        long unable = results.stream().filter(r -> r.status() == CheckStatus.UNABLE).count();
        long na = results.stream().filter(r -> r.status() == CheckStatus.NOT_APPLICABLE).count();
        FlowLog.info(log, CheckEngineService.class, "run",
                "stage", "END",
                "runId", runId,
                "result", fail > 0 ? "NON_COMPLIANT" : "COMPLIANT",
                "pass", pass,
                "fail", fail,
                "unable", unable,
                "notApplicable", na,
                "costMs", elapsedMs(startNs));
        return results;
    }

    /** Backward-compatible overload for tests that do not supply a runId. */
    public List<CheckResult> run(LcTerms lc, InvoiceFields invoice) {
        return run("unknown", lc, invoice);
    }

    private CheckResult executeSafely(String runId, DocumentCheck check, LcTerms lc, InvoiceFields invoice) {
        try {
            CheckResult result = check.execute(lc, invoice);
            logCheckResult(runId, check.checkId(), result);
            return result;
        } catch (Exception e) {
            FlowLog.warn(log, CheckEngineService.class, "executeSafely",
                    "stage", "STEP",
                    "runId", runId,
                    "step", check.checkId(),
                    "result", "UNABLE",
                    "errorMessage", e.getMessage());
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
                    "reason", result.message());
            return;
        }
        FlowLog.info(log, CheckEngineService.class, "executeSafely",
                "stage", "STEP",
                "runId", runId,
                "step", checkId,
                "result", result.status().name());
    }

    private static long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }
}
