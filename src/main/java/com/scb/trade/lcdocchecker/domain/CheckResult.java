package com.scb.trade.lcdocchecker.domain;

/**
 * Result of executing one {@code DocumentCheck}. A {@link CheckStatus#FAIL} carries a
 * {@link Discrepancy}; {@link CheckStatus#UNABLE} carries a human-review message.
 */
public record CheckResult(String checkId, CheckStatus status, Discrepancy discrepancy, String message) {

    public static CheckResult pass(String checkId) {
        return new CheckResult(checkId, CheckStatus.PASS, null, null);
    }

    public static CheckResult fail(String checkId, Discrepancy discrepancy) {
        return new CheckResult(checkId, CheckStatus.FAIL, discrepancy, null);
    }

    /** Term does not apply to this presentation (e.g. an optional field is absent). */
    public static CheckResult notApplicable(String checkId, String reason) {
        return new CheckResult(checkId, CheckStatus.NOT_APPLICABLE, null, reason);
    }

    /** Check cannot be completed programmatically — route to a human compliance officer. */
    public static CheckResult unable(String checkId, String reason) {
        return new CheckResult(checkId, CheckStatus.UNABLE, null, reason);
    }
}
