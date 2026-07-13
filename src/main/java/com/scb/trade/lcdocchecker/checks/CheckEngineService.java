package com.scb.trade.lcdocchecker.checks;

import com.scb.trade.lcdocchecker.config.RulesProperties;
import com.scb.trade.lcdocchecker.domain.CheckResult;
import com.scb.trade.lcdocchecker.domain.InvoiceFields;
import com.scb.trade.lcdocchecker.domain.LcTerms;
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

    public List<CheckResult> run(LcTerms lc, InvoiceFields invoice) {
        return checks.stream()
                .filter(c -> enabled == null || enabled.contains(c.checkId()))
                .map(c -> executeSafely(c, lc, invoice))
                .toList();
    }

    private CheckResult executeSafely(DocumentCheck check, LcTerms lc, InvoiceFields invoice) {
        try {
            return check.execute(lc, invoice);
        } catch (Exception e) {
            log.warn("Check '{}' threw and was downgraded to UNABLE: {}", check.checkId(), e.getMessage());
            return CheckResult.unable(check.checkId(),
                    "Check '" + check.checkId() + "' could not be completed: " + e.getMessage());
        }
    }
}
