package com.scb.trade.lcdocchecker.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single non-compliance finding, serialised with snake_case keys to match the
 * case-study output contract and {@code docs/test_fixtures/expected-results.json}.
 *
 * <p>{@link JsonAlias} accepts camelCase on deserialisation so the model tolerates both
 * the rules.md (camelCase) and AI-Engineer (snake_case) naming contracts
 * (see claude-code-goal.md §3.1 dynamic-alias mechanism).
 *
 * <p>{@code presentedValue} is nullable; a missing presented value serialises as JSON
 * {@code null}, never the string {@code "null"}.
 */
public record Discrepancy(
        String field,
        @JsonProperty("lc_value") @JsonAlias("lcValue") String lcValue,
        @JsonProperty("presented_value") @JsonAlias("presentedValue") String presentedValue,
        @JsonProperty("rule_reference") @JsonAlias("ruleReference") String ruleReference,
        String description) {

    public static Discrepancy of(String field, String lcValue, String presentedValue,
                                 String ruleReference, String description) {
        return new Discrepancy(field, lcValue, presentedValue, ruleReference, description);
    }
}
