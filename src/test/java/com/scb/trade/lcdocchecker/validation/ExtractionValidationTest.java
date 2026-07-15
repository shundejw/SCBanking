package com.scb.trade.lcdocchecker.validation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtractionValidationTest {

    private static ValidationFinding err(String code) {
        return ValidationFinding.of(Severity.ERROR, code, code + " message", "field");
    }

    private static ValidationFinding warn(String code) {
        return ValidationFinding.of(Severity.WARNING, code, code + " message", "field");
    }

    @Test
    void emptyFindings_yieldOk() {
        ExtractionValidation v = ExtractionValidation.of(List.of());
        assertThat(v.status()).isEqualTo(ValidationStatus.OK);
        assertThat(v.findings()).isEmpty();
    }

    @Test
    void nullFindings_yieldOk() {
        ExtractionValidation v = ExtractionValidation.of(null);
        assertThat(v.status()).isEqualTo(ValidationStatus.OK);
        assertThat(v.findings()).isEmpty();
    }

    @Test
    void onlyWarnings_yieldWarn() {
        ExtractionValidation v = ExtractionValidation.of(List.of(warn("W1"), warn("W2")));
        assertThat(v.status()).isEqualTo(ValidationStatus.WARN);
        assertThat(v.findings()).hasSize(2);
    }

    @Test
    void anyError_yieldsFail_evenWhenWarningsPresent() {
        ExtractionValidation v = ExtractionValidation.of(List.of(warn("W1"), err("E1"), warn("W2")));
        assertThat(v.status()).isEqualTo(ValidationStatus.FAIL);
    }

    @Test
    void findingsAreDefensivelyCopied_andImmutable() {
        List<ValidationFinding> source = new ArrayList<>(List.of(warn("W1")));
        ExtractionValidation v = ExtractionValidation.of(source);
        source.add(err("E1")); // mutating the original must not affect the result
        assertThat(v.findings()).hasSize(1);
        assertThat(v.status()).isEqualTo(ValidationStatus.WARN);
        assertThatThrownBy(() -> v.findings().add(err("E2")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullFindingElement_isRejected() {
        List<ValidationFinding> withNull = new ArrayList<>();
        withNull.add(null);
        assertThatThrownBy(() -> ExtractionValidation.of(withNull))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void findingRejectsBlankRuleCodeAndMessage() {
        assertThatThrownBy(() -> ValidationFinding.of(Severity.ERROR, "", "message", "field"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ValidationFinding.of(Severity.ERROR, "CODE", "  ", "field"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
