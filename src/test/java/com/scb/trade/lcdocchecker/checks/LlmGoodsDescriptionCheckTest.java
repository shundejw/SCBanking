package com.scb.trade.lcdocchecker.checks;

import com.scb.trade.lcdocchecker.domain.CheckResult;
import com.scb.trade.lcdocchecker.domain.CheckStatus;
import com.scb.trade.lcdocchecker.domain.GoodsDescriptionVerdict;
import com.scb.trade.lcdocchecker.domain.InvoiceFields;
import com.scb.trade.lcdocchecker.domain.LcTerms;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the four-state contract of {@link LlmGoodsDescriptionCheck}:
 * NOT_APPLICABLE (description missing) / PASS (corresponds=true) / FAIL (false+reason) /
 * UNABLE (exception or null verdict). The LLM call is stubbed by overriding {@code callLlm}
 * (a protected test seam) — the ChatClient fluent chain itself is exercised by the
 * integration test, not here.
 *
 * <p>LcTerms is a final record (not mockable); a minimal real instance is built with only
 * {@code goodsDescription} set, since that is the sole field this check reads.
 */
class LlmGoodsDescriptionCheckTest {

    private static final Resource PROMPT = new ClassPathResource("prompts/goods-description-check-v1.st");

    /** Minimal real LcTerms; only goodsDescription is set (the sole field this check reads). */
    private static LcTerms lcWith(String goods) {
        return new LcTerms(
                "LC-TEST", null, null, null, null, null,
                "Applicant", null, "Beneficiary", null,
                "USD", new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                false, null, null, null,
                goods, null, null, null, null);
    }

    private InvoiceFields invoiceWith(String goods) {
        return InvoiceFields.builder().goodsDescription(goods).build();
    }

    private LlmGoodsDescriptionCheck checkReturning(GoodsDescriptionVerdict v) {
        return new LlmGoodsDescriptionCheck(null, null, PROMPT) {
            @Override protected GoodsDescriptionVerdict callLlm(String lcGoods, String invoiceGoods) {
                return v;
            }
        };
    }

    private LlmGoodsDescriptionCheck checkThrowing(RuntimeException e) {
        return new LlmGoodsDescriptionCheck(null, null, PROMPT) {
            @Override protected GoodsDescriptionVerdict callLlm(String lcGoods, String invoiceGoods) {
                throw e;
            }
        };
    }

    @Test
    void blankLcDescriptionIsNotApplicable() {
        CheckResult r = checkReturning(new GoodsDescriptionVerdict(true, "x"))
                .execute(lcWith("   "), invoiceWith("goods"));
        assertEquals(CheckStatus.NOT_APPLICABLE, r.status());
    }

    @Test
    void blankInvoiceDescriptionIsNotApplicable() {
        CheckResult r = checkReturning(new GoodsDescriptionVerdict(true, "x"))
                .execute(lcWith("lc goods"), invoiceWith(""));
        assertEquals(CheckStatus.NOT_APPLICABLE, r.status());
    }

    @Test
    void correspondsTruePasses() {
        CheckResult r = checkReturning(new GoodsDescriptionVerdict(true, "no material conflict"))
                .execute(lcWith("100 MT REFINED SUGAR"), invoiceWith("100 MT REFINED SUGAR CIF"));
        assertEquals(CheckStatus.PASS, r.status());
    }

    @Test
    void correspondsFalseFailsWithReasonAsDescription() {
        CheckResult r = checkReturning(new GoodsDescriptionVerdict(false, "model IW-2024 vs IW-2025"))
                .execute(lcWith("WIDGETS IW-2024"), invoiceWith("WIDGETS IW-2025"));
        assertEquals(CheckStatus.FAIL, r.status());
        assertEquals("goods_description", r.discrepancy().field());
        assertEquals("model IW-2024 vs IW-2025", r.discrepancy().description());
        assertEquals("WIDGETS IW-2024", r.discrepancy().lcValue());
        assertEquals("WIDGETS IW-2025", r.discrepancy().presentedValue());
    }

    @Test
    void exceptionDegradesToUnable() {
        CheckResult r = checkThrowing(new RuntimeException("LLM timed out"))
                .execute(lcWith("lc goods"), invoiceWith("inv goods"));
        assertEquals(CheckStatus.UNABLE, r.status());
    }

    @Test
    void nullCorrespondsDegradesToUnable() {
        CheckResult r = checkReturning(new GoodsDescriptionVerdict(null, null))
                .execute(lcWith("lc goods"), invoiceWith("inv goods"));
        assertEquals(CheckStatus.UNABLE, r.status());
    }

    @Test
    void nullVerdictDegradesToUnable() {
        CheckResult r = checkReturning(null)
                .execute(lcWith("lc goods"), invoiceWith("inv goods"));
        assertEquals(CheckStatus.UNABLE, r.status());
    }
}
