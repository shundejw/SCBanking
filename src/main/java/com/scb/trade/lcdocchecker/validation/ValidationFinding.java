package com.scb.trade.lcdocchecker.validation;

import java.util.List;

/**
 * A single deterministic field-quality finding. Covers field contract / format / intra-field
 * self-consistency only — never cross-document UCP/ISBP semantics and never a fidelity proof
 * (a wrong-but-self-consistent value is not detectable here).
 *
 * @param ruleCode   stable machine code, e.g. {@code CURRENCY_NOT_ISO4217}
 * @param severity   {@link Severity#ERROR} or {@link Severity#WARNING}
 * @param fieldPaths dotted field paths the finding relates to (may be empty, never null)
 * @param message    human-readable detail
 */
public record ValidationFinding(
        String ruleCode,
        Severity severity,
        List<String> fieldPaths,
        String message) {

    public ValidationFinding {
        if (ruleCode == null || ruleCode.isBlank()) {
            throw new IllegalArgumentException("ruleCode must not be blank");
        }
        if (severity == null) {
            throw new IllegalArgumentException("severity must not be null");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        fieldPaths = fieldPaths == null ? List.of() : List.copyOf(fieldPaths);
    }

    /** Convenience factory; varargs field paths copied defensively. */
    public static ValidationFinding of(Severity severity, String ruleCode, String message, String... fieldPaths) {
        return new ValidationFinding(ruleCode, severity, List.of(fieldPaths), message);
    }
}
