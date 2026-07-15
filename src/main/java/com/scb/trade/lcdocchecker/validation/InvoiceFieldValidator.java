package com.scb.trade.lcdocchecker.validation;

import com.scb.trade.lcdocchecker.config.ValidationProperties;
import com.scb.trade.lcdocchecker.domain.DocumentType;
import com.scb.trade.lcdocchecker.domain.InvoiceFields;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic field-quality guardrail run AFTER LLM extraction. Checks field contract and
 * intra-field self-consistency only — never cross-document UCP/ISBP semantics (those belong to
 * the discrepancy checks) and never a fidelity proof (a wrong-but-self-consistent value passes
 * silently). The math rule {@code quantity × unitPrice ≈ totalAmount} is intentionally absent:
 * the model is flat (no line/net/tax fields) and {@code quantity} carries units.
 *
 * <p>Conservative by design: absent optional fields are skipped, not flagged. Result is a
 * data-quality signal ({@link ExtractionValidation}); the caller logs it.
 */
@Component
public class InvoiceFieldValidator implements FieldValidator<InvoiceFields> {

    private final ValidationProperties props;
    private final Clock clock;

    public InvoiceFieldValidator(ValidationProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    @Override
    public DocumentType documentType() {
        return DocumentType.INVOICE;
    }

    @Override
    public ExtractionValidation validate(InvoiceFields fields) {
        if (fields == null) {
            return ExtractionValidation.ok();
        }
        List<ValidationFinding> findings = new ArrayList<>();
        findings.addAll(checkCurrency(fields.currency()));
        findings.addAll(checkInvoiceDate(fields.invoiceDate()));
        findings.addAll(checkInvoiceNumber(fields.invoiceNumber()));
        findings.addAll(checkTotalAmount(fields.totalAmount()));
        return ExtractionValidation.of(findings);
    }

    private List<ValidationFinding> checkCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return List.of();
        }
        String code = currency.trim().toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
            return List.of(ValidationFinding.of(Severity.ERROR, "CURRENCY_NOT_ISO4217",
                    "Currency '" + currency + "' is not a valid ISO 4217 code.", "currency"));
        }
        return List.of();
    }

    private List<ValidationFinding> checkInvoiceDate(String invoiceDate) {
        if (invoiceDate == null || invoiceDate.isBlank()) {
            return List.of();
        }
        String trimmed = invoiceDate.trim();
        LocalDate parsed = parseDate(trimmed);
        if (parsed == null) {
            return List.of(ValidationFinding.of(Severity.ERROR, "INVOICE_DATE_UNPARSEABLE",
                    "Invoice date '" + trimmed + "' does not match any configured date format.",
                    "invoiceDate"));
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate latestAllowed = today.plusDays(props.allowedFutureSkew().toDays());
        if (parsed.isAfter(latestAllowed)) {
            return List.of(ValidationFinding.of(Severity.WARNING, "INVOICE_DATE_IN_FUTURE",
                    "Invoice date " + parsed + " is beyond the allowed future skew from today " + today + ".",
                    "invoiceDate"));
        }
        return List.of();
    }

    private LocalDate parseDate(String value) {
        for (String pattern : props.invoiceDatePatterns()) {
            try {
                return LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern, Locale.ROOT));
            } catch (DateTimeParseException ignored) {
                // try next configured pattern
            }
        }
        return null;
    }

    private List<ValidationFinding> checkInvoiceNumber(String invoiceNumber) {
        if (invoiceNumber == null || invoiceNumber.isBlank()) {
            return List.of(ValidationFinding.of(Severity.WARNING, "INVOICE_NUMBER_EMPTY",
                    "Invoice number is absent.", "invoiceNumber"));
        }
        List<ValidationFinding> findings = new ArrayList<>();
        if (invoiceNumber.length() > props.invoiceNumberMaxLength()) {
            findings.add(ValidationFinding.of(Severity.WARNING, "INVOICE_NUMBER_TOO_LONG",
                    "Invoice number length " + invoiceNumber.length()
                            + " exceeds maximum " + props.invoiceNumberMaxLength() + ".",
                    "invoiceNumber"));
        }
        if (invoiceNumber.codePoints().anyMatch(Character::isISOControl)) {
            findings.add(ValidationFinding.of(Severity.WARNING, "INVOICE_NUMBER_CONTROL_CHARS",
                    "Invoice number contains control characters.", "invoiceNumber"));
        }
        return findings;
    }

    private List<ValidationFinding> checkTotalAmount(BigDecimal totalAmount) {
        if (totalAmount == null) {
            return List.of();
        }
        if (totalAmount.signum() < 0) {
            return List.of(ValidationFinding.of(Severity.WARNING, "TOTAL_AMOUNT_NEGATIVE",
                    "Total amount is negative: " + totalAmount.toPlainString() + ".", "totalAmount"));
        }
        return List.of();
    }
}
