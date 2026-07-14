package com.scb.trade.lcdocchecker.checks;

import com.scb.trade.lcdocchecker.domain.CheckStatus;
import com.scb.trade.lcdocchecker.domain.InvoiceFields;
import com.scb.trade.lcdocchecker.domain.LcTerms;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link GoodsDescriptionCheck} — UCP 600 Art. 18(c) correspondence at the token
 * level, including the OCR-space-loss case where a scanned invoice loses inter-word spaces while
 * preserving identical content.
 */
class GoodsDescriptionCheckTest {

    private final GoodsDescriptionCheck check = new GoodsDescriptionCheck();

    private static final String LC_DESC = "100 METRIC TONS OF REFINED SUGAR\nINCOTERMS 2020 CIF HAMBURG";

    private static LcTerms lc(String goodsDesc) {
        return new LcTerms(
                "LC123", null, null, null, null, null,
                "APPLICANT", null, "BENEFICIARY", null,
                "USD", new BigDecimal("57500.00"),
                new BigDecimal("5"), new BigDecimal("5"), false,
                null, null, null, goodsDesc, null, null, null, "METRIC TONS");
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

    /** Baseline: identical spaced descriptions correspond. */
    @Test
    void identicalSpacedDescriptionsCorrespond() {
        assertEquals(CheckStatus.PASS, check.execute(lc(LC_DESC), invoice(LC_DESC)).status());
    }

    /**
     * OCR-scanned invoices frequently lose inter-word spaces (PaddleOCR may concatenate tokens).
     * The description must still correspond when content is identical modulo whitespace/punctuation.
     */
    @Test
    void ocrScannedWithoutSpacesCorresponds() {
        String ocrNoSpaces = "100METRICTONSOFREFINEDSUGAR,INCOTERMS2020CIFHAMBURG";
        assertEquals(CheckStatus.PASS, check.execute(lc(LC_DESC), invoice(ocrNoSpaces)).status());
    }

    /** Regression guard (mirrors invoice-goods-model-mismatch): a real word change is still a discrepancy. */
    @Test
    void realWordMismatchFails() {
        String inv = "100 METRIC TONS OF BROWN SUGAR, INCOTERMS 2020 CIF HAMBURG";
        assertEquals(CheckStatus.FAIL, check.execute(lc(LC_DESC), invoice(inv)).status());
    }

    /** Regression guard (mirrors invoice-goods-quantity-mismatch): a quantity number change is still a discrepancy. */
    @Test
    void quantityNumberMismatchFails() {
        String inv = "80 METRIC TONS OF REFINED SUGAR, INCOTERMS 2020 CIF HAMBURG";
        assertEquals(CheckStatus.FAIL, check.execute(lc(LC_DESC), invoice(inv)).status());
    }

    /** Blank description on the invoice side is not applicable. */
    @Test
    void blankInvoiceDescriptionIsNotApplicable() {
        assertEquals(CheckStatus.NOT_APPLICABLE, check.execute(lc(LC_DESC), invoice("   ")).status());
    }
}
