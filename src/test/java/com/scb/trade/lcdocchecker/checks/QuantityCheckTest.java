package com.scb.trade.lcdocchecker.checks;

import com.scb.trade.lcdocchecker.domain.CheckStatus;
import com.scb.trade.lcdocchecker.domain.InvoiceFields;
import com.scb.trade.lcdocchecker.domain.LcTerms;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link QuantityCheck} — verifies the conservative Art. 30 tolerance behaviour
 * (PASS within band, FAIL outside, NOT_APPLICABLE when the quantity is not stably identifiable).
 */
class QuantityCheckTest {

    private final QuantityCheck check = new QuantityCheck();

    /** LC :45A: "100 METRIC TONS OF REFINED SUGAR", :39A: 5/5 → band [95.0, 105.0]. */
    private static LcTerms lc(String goodsDesc, String unit, String plus, String minus) {
        return new LcTerms(
                "LC123", null, null, null, null, null,
                "APPLICANT", null, "BENEFICIARY", null,
                "USD", new BigDecimal("57500.00"),
                new BigDecimal(plus), new BigDecimal(minus), false,
                null, null, null, goodsDesc, null, null, null, unit);
    }

    private static InvoiceFields invoice(String goodsDesc) {
        return InvoiceFields.builder()
                .sellerName("XYZ EXPORT CO., LTD.")
                .applicantName("ABC IMPORTERS PTE LTD")
                .currency("USD")
                .totalAmount("57500.00")
                .goodsDescription(goodsDesc)
                .build();
    }

    @Test
    void matchingQuantityPasses() {
        LcTerms lc = lc("100 METRIC TONS OF REFINED SUGAR", "METRIC TONS", "5", "5");
        CheckStatus status = check.execute(lc, invoice("100 METRIC TONS OF REFINED SUGAR, CIF HAMBURG")).status();
        assertEquals(CheckStatus.PASS, status);
    }

    @Test
    void quantityWithinTolerancePasses() {
        LcTerms lc = lc("100 METRIC TONS OF REFINED SUGAR", "METRIC TONS", "5", "5");
        // 97 MT is within [95, 105] → tolerated, no discrepancy.
        assertEquals(CheckStatus.PASS, check.execute(lc, invoice("97 METRIC TONS OF REFINED SUGAR")).status());
    }

    @Test
    void quantityBeyondToleranceFails() {
        LcTerms lc = lc("100 METRIC TONS OF REFINED SUGAR", "METRIC TONS", "5", "5");
        var result = check.execute(lc, invoice("80 METRIC TONS OF REFINED SUGAR"));
        assertEquals(CheckStatus.FAIL, result.status());
        assertEquals("quantity", result.discrepancy().field());
        assertEquals("100 METRIC TONS", result.discrepancy().lcValue());
        assertEquals("80 METRIC TONS", result.discrepancy().presentedValue());
        assertEquals("UCP 600 Art. 30(b)", result.discrepancy().ruleReference());
    }

    @Test
    void invoiceWithoutLeadingQuantityIsNotApplicable() {
        LcTerms lc = lc("100 METRIC TONS OF REFINED SUGAR", "METRIC TONS", "5", "5");
        var result = check.execute(lc, invoice("INDUSTRIAL GRADE CENTRIFUGAL WATER PUMPS, MODEL WP-900"));
        assertEquals(CheckStatus.NOT_APPLICABLE, result.status());
        assertNull(result.discrepancy());
    }

    @Test
    void missingLcUnitIsNotApplicable() {
        LcTerms lc = lc("100 METRIC TONS OF REFINED SUGAR", null, "5", "5");
        assertEquals(CheckStatus.NOT_APPLICABLE, check.execute(lc, invoice("100 METRIC TONS OF REFINED SUGAR")).status());
    }

    @Test
    void noToleranceRequiresExactMatch() {
        LcTerms lc = lc("100 METRIC TONS OF REFINED SUGAR", "METRIC TONS", "0", "0");
        // 0/0 tolerance → exact match required; 100 == 100 passes.
        assertEquals(CheckStatus.PASS, check.execute(lc, invoice("100 METRIC TONS OF REFINED SUGAR")).status());
        // 97 with no tolerance → outside [100,100] → fail.
        assertEquals(CheckStatus.FAIL, check.execute(lc, invoice("97 METRIC TONS OF REFINED SUGAR")).status());
    }
}
