package com.scb.trade.lcdocchecker.checks;

import com.scb.trade.lcdocchecker.domain.CheckResult;
import com.scb.trade.lcdocchecker.domain.InvoiceFields;
import com.scb.trade.lcdocchecker.domain.LcTerms;

/**
 * A single, independently-implemented document check. Checks are gathered by Spring and
 * executed by {@code CheckEngineService} in deterministic {@code @Order}.
 */
public interface DocumentCheck {

    /** Stable identifier used by the {@code lcchecker.rules.enabled} allowlist. */
    String checkId();

    /**
     * Evaluate this check against the LC terms and the extracted invoice fields.
     *
     * @return {@link CheckStatus#PASS}, {@link CheckStatus#FAIL} (with a {@code Discrepancy}),
     *         {@link CheckStatus#NOT_APPLICABLE}, or {@link CheckStatus#UNABLE}.
     */
    CheckResult execute(LcTerms lc, InvoiceFields invoice);
}
