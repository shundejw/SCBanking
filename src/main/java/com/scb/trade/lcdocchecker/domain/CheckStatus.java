package com.scb.trade.lcdocchecker.domain;

/**
 * Outcome of a single document check.
 *
 * <ul>
 *   <li>{@link #PASS} — the invoice satisfies this LC term.</li>
 *   <li>{@link #FAIL} — a concrete discrepancy was found (becomes a {@link Discrepancy}).</li>
 *   <li>{@link #NOT_APPLICABLE} — the term does not apply (e.g. optional port not stated).</li>
 *   <li>{@link #UNABLE} — the check cannot be completed programmatically and must be routed
 *       to a human compliance officer (e.g. visual signature verification).</li>
 * </ul>
 */
public enum CheckStatus {
    PASS,
    FAIL,
    NOT_APPLICABLE,
    UNABLE
}
