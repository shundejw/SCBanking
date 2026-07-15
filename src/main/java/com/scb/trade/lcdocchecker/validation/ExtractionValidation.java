package com.scb.trade.lcdocchecker.validation;

import java.util.List;

/**
 * Aggregated extraction-quality result for a single document.
 *
 * <p>This is a DATA-QUALITY signal — not a statistical confidence, and not a UCP/ISBP
 * discrepancy. It is logged / persisted separately and MUST NOT be merged into the
 * {@code CheckReport} response contract ({@code {compliant, discrepancies}}). It can detect
 * field-internal inconsistency but cannot prove a field is faithful to the source.
 */
public record ExtractionValidation(ValidationStatus status, List<ValidationFinding> findings) {

    public ExtractionValidation {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    /**
     * Build from raw findings, deriving the status: any ERROR → FAIL, else any WARNING → WARN,
     * else OK. Defensive copy; null elements are rejected by {@link List#copyOf}.
     */
    public static ExtractionValidation of(List<ValidationFinding> findings) {
        List<ValidationFinding> safe = findings == null ? List.of() : List.copyOf(findings);
        return new ExtractionValidation(derive(safe), safe);
    }

    public static ExtractionValidation ok() {
        return new ExtractionValidation(ValidationStatus.OK, List.of());
    }

    private static ValidationStatus derive(List<ValidationFinding> findings) {
        boolean hasWarning = false;
        for (ValidationFinding f : findings) {
            if (f.severity() == Severity.ERROR) {
                return ValidationStatus.FAIL;
            }
            if (f.severity() == Severity.WARNING) {
                hasWarning = true;
            }
        }
        return hasWarning ? ValidationStatus.WARN : ValidationStatus.OK;
    }
}
