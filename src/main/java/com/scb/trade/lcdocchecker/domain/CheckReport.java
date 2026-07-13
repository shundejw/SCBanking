package com.scb.trade.lcdocchecker.domain;

import java.util.List;

/**
 * Compliance report returned by {@code POST /checks}. Serialises to the exact output
 * contract: {@code {"compliant": <bool>, "discrepancies": [ ... ]}}.
 *
 * <p>Non-discrepancy routing signals (e.g. a signature {@link CheckStatus#UNABLE} that
 * requires manual compliance review) are NOT included here; they are persisted in the
 * {@code check_results} artifact and surfaced via the inspection API, keeping the response
 * body aligned with the case-study contract.
 */
public record CheckReport(boolean compliant, List<Discrepancy> discrepancies) {

    public CheckReport(boolean compliant, List<Discrepancy> discrepancies) {
        this.compliant = compliant;
        this.discrepancies = discrepancies == null ? List.of() : List.copyOf(discrepancies);
    }

    public static CheckReport of(List<Discrepancy> discrepancies) {
        return new CheckReport(discrepancies.isEmpty(), discrepancies);
    }
}
