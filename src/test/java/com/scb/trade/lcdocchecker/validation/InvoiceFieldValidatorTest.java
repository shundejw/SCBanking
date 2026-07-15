package com.scb.trade.lcdocchecker.validation;

import com.scb.trade.lcdocchecker.config.ValidationProperties;
import com.scb.trade.lcdocchecker.domain.InvoiceFields;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceFieldValidatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 15);
    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay(ZONE).toInstant(), ZONE);

    private InvoiceFieldValidator validator;

    @BeforeEach
    void setUp() {
        ValidationProperties props = new ValidationProperties("UTC", Duration.ofDays(7), 50,
                List.of("yyyy-MM-dd", "dd/MM/yyyy", "dd-MMM-yyyy"));
        validator = new InvoiceFieldValidator(props, CLOCK);
    }

    private InvoiceFields.Builder baseBuilder() {
        return InvoiceFields.builder()
                .currency("USD")
                .invoiceNumber("INV-001")
                .invoiceDate("2026-07-01")
                .totalAmount(new BigDecimal("1000.00"));
    }

    @Test
    void cleanInvoice_yieldsOk() {
        ExtractionValidation v = validator.validate(baseBuilder().build());
        assertThat(v.status()).isEqualTo(ValidationStatus.OK);
        assertThat(v.findings()).isEmpty();
    }

    @Test
    void nonIsoCurrency_yieldsFail() {
        ExtractionValidation v = validator.validate(baseBuilder().currency("ABC").build());
        assertThat(v.status()).isEqualTo(ValidationStatus.FAIL);
        assertThat(v.findings()).extracting(ValidationFinding::ruleCode).contains("CURRENCY_NOT_ISO4217");
    }

    @Test
    void structurallyInvalidCurrency_yieldsFail() {
        ExtractionValidation v = validator.validate(baseBuilder().currency("US").build());
        assertThat(v.status()).isEqualTo(ValidationStatus.FAIL);
    }

    @Test
    void lowercaseCurrency_isUppercasedAndAccepted() {
        ExtractionValidation v = validator.validate(baseBuilder().currency("usd").build());
        assertThat(v.status()).isEqualTo(ValidationStatus.OK);
    }

    @Test
    void unparseableDate_yieldsFail() {
        ExtractionValidation v = validator.validate(baseBuilder().invoiceDate("not-a-date").build());
        assertThat(v.status()).isEqualTo(ValidationStatus.FAIL);
        assertThat(v.findings()).extracting(ValidationFinding::ruleCode).contains("INVOICE_DATE_UNPARSEABLE");
    }

    @Test
    void futureDateBeyondSkew_yieldsWarn() {
        // today=2026-07-15, skew=7d → latestAllowed=2026-07-22
        ExtractionValidation v = validator.validate(baseBuilder().invoiceDate("2026-12-01").build());
        assertThat(v.status()).isEqualTo(ValidationStatus.WARN);
        assertThat(v.findings()).extracting(ValidationFinding::ruleCode).contains("INVOICE_DATE_IN_FUTURE");
    }

    @Test
    void dateWithinSkew_isOk() {
        ExtractionValidation v = validator.validate(baseBuilder().invoiceDate("2026-07-20").build());
        assertThat(v.status()).isEqualTo(ValidationStatus.OK);
    }

    @Test
    void emptyInvoiceNumber_yieldsWarn() {
        ExtractionValidation v = validator.validate(baseBuilder().invoiceNumber("").build());
        assertThat(v.status()).isEqualTo(ValidationStatus.WARN);
        assertThat(v.findings()).extracting(ValidationFinding::ruleCode).contains("INVOICE_NUMBER_EMPTY");
    }

    @Test
    void tooLongInvoiceNumber_yieldsWarn() {
        ExtractionValidation v = validator.validate(baseBuilder().invoiceNumber("A".repeat(51)).build());
        assertThat(v.findings()).extracting(ValidationFinding::ruleCode).contains("INVOICE_NUMBER_TOO_LONG");
    }

    @Test
    void invoiceNumberWithControlChar_yieldsWarn() {
        ExtractionValidation v = validator.validate(baseBuilder().invoiceNumber("INVX").build());
        assertThat(v.findings()).extracting(ValidationFinding::ruleCode).contains("INVOICE_NUMBER_CONTROL_CHARS");
    }

    @Test
    void negativeTotalAmount_yieldsWarn() {
        ExtractionValidation v = validator.validate(baseBuilder().totalAmount(new BigDecimal("-50.00")).build());
        assertThat(v.status()).isEqualTo(ValidationStatus.WARN);
        assertThat(v.findings()).extracting(ValidationFinding::ruleCode).contains("TOTAL_AMOUNT_NEGATIVE");
    }

    @Test
    void nonAsciiInvoiceNumber_isAccepted() {
        ExtractionValidation v = validator.validate(baseBuilder().invoiceNumber("发票-2026-001").build());
        assertThat(v.findings()).isEmpty();
        assertThat(v.status()).isEqualTo(ValidationStatus.OK);
    }

    @Test
    void nullFields_yieldOk() {
        assertThat(validator.validate(null).status()).isEqualTo(ValidationStatus.OK);
    }

    @Test
    void trulyOptionalFieldsSkipped_butInvoiceNumberAbsence_isFlagged() {
        // currency / totalAmount / invoiceDate absence → no finding (skipped);
        // invoiceNumber absence → WARNING (it is a core field, flagged but not a hard fail).
        ExtractionValidation v = validator.validate(InvoiceFields.builder()
                .currency("USD")
                .totalAmount(new BigDecimal("100.00"))
                .build());
        assertThat(v.findings()).extracting(ValidationFinding::ruleCode)
                .containsExactly("INVOICE_NUMBER_EMPTY");
        assertThat(v.status()).isEqualTo(ValidationStatus.WARN);
    }
}
