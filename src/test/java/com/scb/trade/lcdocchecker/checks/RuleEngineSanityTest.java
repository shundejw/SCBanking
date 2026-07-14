package com.scb.trade.lcdocchecker.checks;

import com.scb.trade.lcdocchecker.domain.CheckReport;
import com.scb.trade.lcdocchecker.domain.Discrepancy;
import com.scb.trade.lcdocchecker.domain.InvoiceFields;
import com.scb.trade.lcdocchecker.domain.LcTerms;
import com.scb.trade.lcdocchecker.parser.LcParserService;
import com.scb.trade.lcdocchecker.report.ReportAssemblerService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end sanity for the rule engine against the real main-baseline LC, using
 * hand-built {@link InvoiceFields} (standing in for the mocked LLM extraction). The full
 * 16-case matrix is exercised by {@code ExpectedResultsManifestTest}.
 */
class RuleEngineSanityTest {

    private static LcTerms mainLc;
    private final CheckEngineService engine = new CheckEngineService(allChecks());
    private final ReportAssemblerService assembler = new ReportAssemblerService();

    private static List<DocumentCheck> allChecks() {
        return List.of(
                new AmountCheck(),
                new CurrencyCheck(),
                new IssuerNameCheck(),
                new SignatureCheck(),
                new ApplicantNameCheck(),
                new AddressCountryCheck(),
                new GoodsDescriptionCheck(),
                new QuantityCheck(),
                new PortOfLoadingCheck(),
                new PortOfDischargeCheck(),
                new LcReferenceCheck());
    }

    @BeforeAll
    static void parseMainBaseline() throws Exception {
        String mt700 = Files.readString(Path.of("docs/test_fixtures/lc/SWIFT_MT700_Sample_Compliant.mt700"));
        mainLc = new LcParserService().parse(mt700);
    }

    private CheckReport run(InvoiceFields inv) {
        return assembler.assemble(engine.run(mainLc, inv));
    }

    @Test
    void compliantInvoiceHasNoDiscrepancies() {
        InvoiceFields inv = baseCompliant();
        CheckReport report = run(inv);
        assertTrue(report.compliant(), () -> "discrepancies=" + report.discrepancies());
        assertEquals(0, report.discrepancies().size());
    }

    @Test
    void quantityMismatchProducesGoodsAndQuantityDiscrepancies() {
        InvoiceFields inv = baseCompliant().toBuilder()
                .goodsDescription("80 METRIC TONS OF REFINED SUGAR, INCOTERMS 2020 CIF HAMBURG")
                .build();
        CheckReport report = run(inv);
        assertEquals(2, report.discrepancies().size(), () -> "disc=" + report.discrepancies());
        Discrepancy goods = report.discrepancies().get(0);
        assertEquals("goodsDescription", goods.field());
        assertEquals("100 METRIC TONS OF REFINED SUGAR\nINCOTERMS 2020 CIF HAMBURG", goods.lcValue());
        assertEquals("80 METRIC TONS OF REFINED SUGAR, INCOTERMS 2020 CIF HAMBURG", goods.presentedValue());
        assertEquals("UCP 600 Art. 18(c)", goods.ruleReference());

        Discrepancy quantity = report.discrepancies().get(1);
        assertEquals("quantity", quantity.field());
        assertEquals("100 METRIC TONS", quantity.lcValue());
        assertEquals("80 METRIC TONS", quantity.presentedValue());
        assertEquals("UCP 600 Art. 30(b)", quantity.ruleReference());
    }

    @Test
    void amountExceedsProducesTotalAmountDiscrepancy() {
        InvoiceFields inv = InvoiceFields.builder()
                .sellerName("XYZ EXPORT CO., LTD.")
                .applicantName("ABC IMPORTERS PTE LTD")
                .currency("USD")
                .totalAmount("63000.00")
                .goodsDescription("100 METRIC TONS OF REFINED SUGAR, INCOTERMS 2020 CIF HAMBURG")
                .portOfLoading("Port of Singapore")
                .portOfDischarge("Port of Hamburg")
                .lcReferenceNumber("LC202607120001")
                .build();

        CheckReport report = run(inv);
        assertFalse(report.compliant());
        assertEquals(1, report.discrepancies().size());
        Discrepancy d = report.discrepancies().get(0);
        assertEquals("totalAmount", d.field());
        assertEquals("Max Allowed: 60375.00", d.lcValue());
        assertEquals("63000.00", d.presentedValue());
        assertEquals("UCP 600 Art. 18(b)", d.ruleReference());
    }

    @Test
    void sellerMismatchProducesIssuerNameDiscrepancy() {
        InvoiceFields inv = baseCompliant().toBuilder().sellerName("ACME SUGAR TRADING LTD").build();
        CheckReport report = run(inv);
        assertEquals(1, report.discrepancies().size(), () -> "disc=" + report.discrepancies());
        Discrepancy d = report.discrepancies().get(0);
        assertEquals("issuerName", d.field());
        assertEquals("XYZ EXPORT CO., LTD.", d.lcValue());
        assertEquals("ACME SUGAR TRADING LTD", d.presentedValue());
        assertEquals("UCP 600 Art. 18(a)(i)", d.ruleReference());
    }

    @Test
    void goodsModelMismatchProducesGoodsDiscrepancy() {
        InvoiceFields inv = baseCompliant().toBuilder()
                .goodsDescription("100 METRIC TONS OF BROWN SUGAR, INCOTERMS 2020 CIF HAMBURG")
                .build();
        CheckReport report = run(inv);
        Discrepancy d = assertOne(report, "goodsDescription");
        assertEquals("100 METRIC TONS OF REFINED SUGAR\nINCOTERMS 2020 CIF HAMBURG", d.lcValue());
        assertEquals("UCP 600 Art. 18(c)", d.ruleReference());
    }

    @Test
    void currencyMismatchDoesNotAlsoFlagAmount() {
        // total 57500 is within tolerance → only currency discrepancy fires.
        InvoiceFields inv = baseCompliant().toBuilder()
                .currency("EUR")
                .build();
        CheckReport report = run(inv);
        assertEquals(1, report.discrepancies().size(), () -> "disc=" + report.discrepancies());
        assertEquals("currency", report.discrepancies().get(0).field());
    }

    private static InvoiceFields baseCompliant() {
        return InvoiceFields.builder()
                .sellerName("XYZ EXPORT CO., LTD.")
                .applicantName("ABC IMPORTERS PTE LTD")
                .currency("USD")
                .totalAmount("57500.00")
                .goodsDescription("100 METRIC TONS OF REFINED SUGAR, INCOTERMS 2020 CIF HAMBURG")
                .portOfLoading("Port of Singapore")
                .portOfDischarge("Port of Hamburg")
                .lcReferenceNumber("LC202607120001")
                .build();
    }

    private static Discrepancy assertOne(CheckReport report, String field) {
        assertEquals(1, report.discrepancies().size(), () -> "disc=" + report.discrepancies());
        Discrepancy d = report.discrepancies().get(0);
        assertEquals(field, d.field());
        return d;
    }
}
