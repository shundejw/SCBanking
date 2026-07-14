package com.scb.trade.lcdocchecker.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Structured verdict returned by the LLM goods-description correspondence check
 * (UCP 600 Art. 18(c) / ISBP 821 C3). Used as the structured-output target of the LLM call
 * via {@code .entity(GoodsDescriptionVerdict.class)}.
 *
 * @param corresponds whether the invoice goods description corresponds with the LC's (no conflict)
 * @param reason      one-sentence justification (cited to the {@code description} field on FAIL)
 */
public record GoodsDescriptionVerdict(
        @JsonProperty("corresponds") Boolean corresponds,
        @JsonProperty("reason") String reason) {
}
