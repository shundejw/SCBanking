package com.scb.trade.lcdocchecker;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.scb.trade.lcdocchecker.checks.AmountCheck;
import com.scb.trade.lcdocchecker.checks.AddressCountryCheck;
import com.scb.trade.lcdocchecker.checks.ApplicantNameCheck;
import com.scb.trade.lcdocchecker.checks.CheckEngineService;
import com.scb.trade.lcdocchecker.checks.CurrencyCheck;
import com.scb.trade.lcdocchecker.checks.DocumentCheck;
import com.scb.trade.lcdocchecker.checks.GoodsDescriptionCheck;
import com.scb.trade.lcdocchecker.checks.IssuerNameCheck;
import com.scb.trade.lcdocchecker.checks.LcReferenceCheck;
import com.scb.trade.lcdocchecker.checks.PortOfDischargeCheck;
import com.scb.trade.lcdocchecker.checks.PortOfLoadingCheck;
import com.scb.trade.lcdocchecker.checks.QuantityCheck;
import com.scb.trade.lcdocchecker.checks.SignatureCheck;
import com.scb.trade.lcdocchecker.domain.CheckReport;
import com.scb.trade.lcdocchecker.domain.Discrepancy;
import com.scb.trade.lcdocchecker.domain.InvoiceFields;
import com.scb.trade.lcdocchecker.domain.LcTerms;
import com.scb.trade.lcdocchecker.exception.InvalidMt700Exception;
import com.scb.trade.lcdocchecker.parser.LcParserService;
import com.scb.trade.lcdocchecker.report.ReportAssemblerService;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The unified harness over docs/test_fixtures/expected-results.json (goal §6.1).
 *
 * <p>For every document-comparison case it runs the REAL parser + REAL rule engine (with a
 * deterministic per-invoice field stub standing in for the mocked LLM extraction) and asserts
 * the resulting {@code compliant} flag and every discrepancy field matches the manifest. The
 * invalid-MT700 case asserts the parser throws the exact mandated message.
 */
class ExpectedResultsManifestTest {

    private static final Path FIXTURES = Path.of("docs/test_fixtures");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LcParserService parser = new LcParserService();
    private final CheckEngineService engine = new CheckEngineService(allChecks());
    private final ReportAssemblerService assembler = new ReportAssemblerService();

    /** Deterministic invoice-field stub keyed by fixture filename (derived from PDF content). */
    private static final Map<String, InvoiceFields> FIELDS = Map.ofEntries(
            Map.entry("invoices/invoice-compliant-digital.pdf", mainBase().build()),
            Map.entry("invoices/invoice-compliant-scanned.pdf", mainBase().build()),
            Map.entry("invoices/invoice-amount-exceeds.pdf", mainBase().totalAmount("63000.00").build()),
            Map.entry("invoices/invoice-goods-model-mismatch.pdf",
                    mainBase().goodsDescription("100 METRIC TONS OF BROWN SUGAR, INCOTERMS 2020 CIF HAMBURG").build()),
            Map.entry("invoices/invoice-goods-quantity-mismatch.pdf",
                    mainBase().goodsDescription("80 METRIC TONS OF REFINED SUGAR, INCOTERMS 2020 CIF HAMBURG").build()),
            Map.entry("invoices/invoice-seller-mismatch.pdf", mainBase().sellerName("ACME SUGAR TRADING LTD").build()),
            Map.entry("invoices/invoice-buyer-mismatch.pdf", mainBase().applicantName("NORDIC TRADING ASIA PTE LTD").build()),
            Map.entry("invoices/invoice-loading-location-mismatch.pdf", mainBase().portOfLoading("Port of Shanghai").build()),
            Map.entry("invoices/invoice-destination-mismatch.pdf", mainBase().portOfDischarge("Port of Rotterdam").build()),
            Map.entry("invoices/invoice-currency-mismatch.pdf", mainBase().currency("EUR").build()),

            Map.entry("invoices/invoice-import-reference-missing.pdf", importRefBase().build()),
            Map.entry("invoices/invoice-import-reference-compliant.pdf",
                    importRefBase().lcReferenceNumber("LCDEMO2026-0002").build()),

            Map.entry("invoices/invoice-compliant-mt700-valid.pdf",
                    InvoiceFields.builder()
                            .sellerName("XYZ EXPORT CO")
                            .applicantName("ABC IMPORT LTD")
                            .currency("USD")
                            .totalAmount("100000.00")
                            .goodsDescription("GOODS AS PER PURCHASE ORDER")
                            .portOfLoading("Shanghai")
                            .portOfDischarge("Singapore")
                            .lcReferenceNumber("LC20260001")
                            .build()));

    private static InvoiceFields.Builder mainBase() {
        return InvoiceFields.builder()
                .sellerName("XYZ EXPORT CO., LTD.")
                .applicantName("ABC IMPORTERS PTE LTD")
                .currency("USD")
                .totalAmount("57500.00")
                .goodsDescription("100 METRIC TONS OF REFINED SUGAR, INCOTERMS 2020 CIF HAMBURG")
                .portOfLoading("Port of Singapore")
                .portOfDischarge("Port of Hamburg")
                .lcReferenceNumber("LC202607120001");
    }

    private static InvoiceFields.Builder importRefBase() {
        return InvoiceFields.builder()
                .sellerName("GLOBAL TRADE LOGISTICS CORP.")
                .applicantName("EURO IMPORT DISTRIBUTION GMBH")
                .currency("USD")
                .totalAmount("57500.00")
                .goodsDescription("INDUSTRIAL GRADE CENTRIFUGAL WATER PUMPS, MODEL WP-900, "
                        + "QUANTITY: 10 UNITS; REINFORCED HIGH-PRESSURE CONNECTING HOSES, 5-METER LENGTHS, QUANTITY: 50 UNITS")
                .portOfLoading("Port of New York, USA")
                .portOfDischarge("Port of Hamburg, Germany")
                .lcReferenceNumber(null);
    }

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

    @TestFactory
    Stream<DynamicTest> manifestCases() throws Exception {
        JsonNode root = MAPPER.readTree(Files.readString(FIXTURES.resolve("expected-results.json")));
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode c : root.get("cases")) {
            String id = c.get("id").asText();
            String category = c.get("category").asText();
            if ("document-comparison".equals(category)) {
                tests.add(DynamicTest.dynamicTest("case: " + id, () -> assertDocumentComparison(c)));
            } else if ("invalid-mt700-missing-32b".equals(id)) {
                tests.add(DynamicTest.dynamicTest("case: " + id, () -> assertInvalidMt700(c)));
            }
            // unreadable-pdf / ocr-insufficient are PDF-layer cases covered by PdfTextExtractorTest
            // and the controller integration test (they surface as HTTP 422 there).
        }
        return tests.stream();
    }

    private void assertDocumentComparison(JsonNode c) throws Exception {
        String lcText = Files.readString(FIXTURES.resolve(c.get("lc_file").asText()));
        InvoiceFields invoice = FIELDS.get(c.get("invoice_file").asText());
        assertNotNull(invoice, "no stub fields for " + c.get("invoice_file").asText());

        LcTerms lc = parser.parse(lcText);
        CheckReport report = assembler.assemble(engine.run(lc, invoice));

        JsonNode expected = c.get("expected").get("response");
        assertEquals(expected.get("compliant").asBoolean(), report.compliant(),
                () -> id(c) + " compliant flag mismatch");

        JsonNode expDisc = expected.get("discrepancies");
        assertEquals(expDisc.size(), report.discrepancies().size(),
                () -> id(c) + " discrepancy count: expected " + expDisc + " but got " + report.discrepancies());

        for (int i = 0; i < expDisc.size(); i++) {
            assertDiscrepancy(id(c), expDisc.get(i), report.discrepancies().get(i));
        }
    }

    private void assertInvalidMt700(JsonNode c) throws Exception {
        String lcText = Files.readString(FIXTURES.resolve(c.get("lc_file").asText()));
        InvalidMt700Exception ex = assertThrows(InvalidMt700Exception.class, () -> parser.parse(lcText));
        JsonNode err = c.get("expected").get("error");
        assertEquals(err.get("message").asText(), "Invalid MT700 format: " + ex.getMessage(),
                () -> id(c) + " invalid-MT700 message");
    }

    private static void assertDiscrepancy(String caseId, JsonNode expected, Discrepancy actual) {
        assertEquals(expected.get("field").asText(), actual.field(), () -> caseId + " field");
        assertEquals(text(expected, "lc_value"), actual.lcValue(), () -> caseId + " lc_value");
        assertEquals(text(expected, "presented_value"), actual.presentedValue(), () -> caseId + " presented_value");
        assertEquals(expected.get("rule_reference").asText(), actual.ruleReference(), () -> caseId + " rule_reference");
        assertEquals(expected.get("description").asText(), actual.description(), () -> caseId + " description");
    }

    /** Treats JSON null / missing as Java null. */
    private static String text(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return (n == null || n.isNull()) ? null : n.asText();
    }

    private static String id(JsonNode c) {
        return c.get("id").asText();
    }
}
