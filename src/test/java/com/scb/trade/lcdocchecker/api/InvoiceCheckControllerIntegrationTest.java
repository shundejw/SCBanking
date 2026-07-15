package com.scb.trade.lcdocchecker.api;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.scb.trade.lcdocchecker.checks.LlmGoodsDescriptionCheck;
import com.scb.trade.lcdocchecker.domain.CheckResult;
import com.scb.trade.lcdocchecker.domain.DocumentType;
import com.scb.trade.lcdocchecker.domain.InvoiceFields;
import com.scb.trade.lcdocchecker.extractor.InvoiceExtractionService;
import com.scb.trade.lcdocchecker.extractor.OcrGateway;
import com.scb.trade.lcdocchecker.extractor.OcrPageRequest;
import com.scb.trade.lcdocchecker.extractor.OcrPageResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full HTTP-layer integration test. The LC parser, PDF text extractor, rule engine,
 * assembler, artifact store, controller and exception handler are all REAL; only the LLM
 * ({@link InvoiceExtractionService}) and the OCR sidecar ({@link OcrGateway}) are mocked,
 * keeping the suite deterministic (goal §6).
 */
@SpringBootTest
@ActiveProfiles("test")
class InvoiceCheckControllerIntegrationTest {

    private static final Path FIXTURES = Path.of("docs/test_fixtures");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @MockitoBean
    private InvoiceExtractionService invoiceExtractionService;

    @MockitoBean
    private OcrGateway ocrGateway;

    @MockitoBean
    private LlmGoodsDescriptionCheck llmGoodsDescriptionCheck;

    @BeforeEach
    void resetMocks() {
        // Boot 4.1 dropped @AutoConfigureMockMvc; build MockMvc from the wired context.
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        // default: OCR returns rich text if ever invoked; individual tests override.
        when(ocrGateway.extractPages(any())).thenReturn(List.of());
        // The mocked InvoiceExtractionService also implements DocumentExtractor; declare its
        // document type so the dispatcher routes INVOICE documents to it.
        when(invoiceExtractionService.documentType()).thenReturn(DocumentType.INVOICE);
        // The LLM goods-description check is mocked here — its real ChatClient call is covered
        // by LlmGoodsDescriptionCheckTest. It degrades to UNABLE so it never produces a
        // discrepancy that would disturb the other assertions in this integration test.
        when(llmGoodsDescriptionCheck.checkId()).thenReturn("llm_goods_description_rule");
        when(llmGoodsDescriptionCheck.appliesTo()).thenReturn(java.util.EnumSet.of(DocumentType.INVOICE));
        when(llmGoodsDescriptionCheck.execute(any(), any()))
                .thenReturn(CheckResult.unable("llm_goods_description_rule", "mocked in integration test"));
    }

    private String lc(String name) throws Exception {
        return Files.readString(FIXTURES.resolve("lc").resolve(name));
    }

    private byte[] invoice(String name) throws Exception {
        return Files.readAllBytes(FIXTURES.resolve("invoices").resolve(name));
    }

    private MockMultipartFile invoicePart(String filename) throws Exception {
        return new MockMultipartFile("invoice", filename, "application/pdf", invoice(filename));
    }

    @Test
    void extractionValidationArtifactIsExposedAfterCheck() throws Exception {
        when(invoiceExtractionService.extract(anyString())).thenReturn(mainCompliant());

        MvcResult result = mockMvc.perform(multipart("/checks")
                        .file(invoicePart("invoice-compliant-digital.pdf"))
                        .param("lc", lc("SWIFT_MT700_Sample_Compliant.mt700")))
                .andExpect(status().isOk())
                .andReturn();
        String runId = result.getResponse().getHeader("X-Check-Run-Id");

        // mainCompliant() omits invoiceNumber -> InvoiceFieldValidator emits INVOICE_NUMBER_EMPTY (WARN),
        // proving the registry-driven validator ran in the orchestrator and the artifact is exposed.
        mockMvc.perform(get("/checks/" + runId + "/artifacts/extraction_validation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WARN"))
                .andExpect(jsonPath("$.findings").isArray())
                .andExpect(jsonPath("$.findings.length()").value(1))
                .andExpect(jsonPath("$.findings[0].ruleCode").value("INVOICE_NUMBER_EMPTY"));
    }

    private InvoiceFields mainCompliant() {
        return InvoiceFields.builder()
                .sellerName("XYZ EXPORT CO., LTD.")
                .applicantName("ABC IMPORTERS PTE LTD")
                .currency("USD")
                .totalAmount(new BigDecimal("57500.00"))
                .goodsDescription("100 METRIC TONS OF REFINED SUGAR, INCOTERMS 2020 CIF HAMBURG")
                .portOfLoading("Port of Singapore")
                .portOfDischarge("Port of Hamburg")
                .lcReferenceNumber("LC202607120001")
                .build();
    }

    @Test
    void compliantDigitalReturns200NoDiscrepancies() throws Exception {
        when(invoiceExtractionService.extract(anyString())).thenReturn(mainCompliant());

        MvcResult result = mockMvc.perform(multipart("/checks")
                        .file(invoicePart("invoice-compliant-digital.pdf"))
                        .param("lc", lc("SWIFT_MT700_Sample_Compliant.mt700")))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Check-Run-Id"))
                .andExpect(jsonPath("$.compliant").value(true))
                .andExpect(jsonPath("$.discrepancies").isArray())
                .andExpect(jsonPath("$.discrepancies.length()").value(0))
                .andReturn();

        String runId = result.getResponse().getHeader("X-Check-Run-Id");

        // GET report by runId
        mockMvc.perform(get("/checks/" + runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compliant").value(true));

        // GET an intermediate artifact
        mockMvc.perform(get("/checks/" + runId + "/artifacts/lc_parsed"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"));
    }

    @Test
    void amountExceedsReturns200WithTotalAmountDiscrepancy() throws Exception {
        InvoiceFields over = InvoiceFields.builder()
                .sellerName("XYZ EXPORT CO., LTD.")
                .applicantName("ABC IMPORTERS PTE LTD")
                .currency("USD")
                .totalAmount(new BigDecimal("63000.00"))
                .goodsDescription("100 METRIC TONS OF REFINED SUGAR, INCOTERMS 2020 CIF HAMBURG")
                .portOfLoading("Port of Singapore")
                .portOfDischarge("Port of Hamburg")
                .lcReferenceNumber("LC202607120001")
                .build();
        when(invoiceExtractionService.extract(anyString())).thenReturn(over);

        mockMvc.perform(multipart("/checks")
                        .file(invoicePart("invoice-amount-exceeds.pdf"))
                        .param("lc", lc("SWIFT_MT700_Sample_Compliant.mt700")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compliant").value(false))
                .andExpect(jsonPath("$.discrepancies.length()").value(1))
                .andExpect(jsonPath("$.discrepancies[0].field").value("invoice_amount"))
                .andExpect(jsonPath("$.discrepancies[0].lc_value").value("USD 57,500.00"))
                .andExpect(jsonPath("$.discrepancies[0].presented_value").value("USD 63,000.00"))
                .andExpect(jsonPath("$.discrepancies[0].rule_reference").value("UCP 600 Art. 18(b)"));
    }

    @Test
    void scannedPdfUsesOcrFallbackThenReturnsCompliant() throws Exception {
        String ocrText = "XYZ EXPORT CO., LTD. ABC IMPORTERS PTE LTD. Currency: USD. "
                + "Description of Goods: 100 METRIC TONS OF REFINED SUGAR CIF HAMBURG. "
                + "Total Invoice Value USD 57,500.00.";
        when(ocrGateway.extractPages(any())).thenAnswer(inv -> {
            List<OcrPageRequest> reqs = inv.getArgument(0);
            return reqs.stream().map(r -> new OcrPageResult(r.pageNumber(), ocrText)).toList();
        });
        when(invoiceExtractionService.extract(anyString())).thenReturn(mainCompliant());

        mockMvc.perform(multipart("/checks")
                        .file(invoicePart("invoice-compliant-scanned.pdf"))
                        .param("lc", lc("SWIFT_MT700_Sample_Compliant.mt700")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compliant").value(true));
    }

    @Test
    void invalidMt700Missing32bReturns422() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/checks")
                        .file(invoicePart("invoice-compliant-digital.pdf"))
                        .param("lc", lc("MT700_Invalid_Missing32B.mt700")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("UNPROCESSABLE_ENTITY"))
                .andReturn();
        JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());
        assertEquals("Invalid MT700 format: mandatory field 32B (Currency Code, Amount) is missing.",
                body.get("message").asText());
    }

    @Test
    void corruptPdfReturns422UnreadableMessage() throws Exception {
        mockMvc.perform(multipart("/checks")
                        .file(invoicePart("invoice-unreadable.pdf"))
                        .param("lc", lc("SWIFT_MT700_Sample_Compliant.mt700")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("UNPROCESSABLE_ENTITY"))
                .andExpect(jsonPath("$.message").value(
                        "PDF could not be rendered for text extraction or OCR; the document may be corrupt, encrypted, or unsupported."));
    }

    @Test
    void ocrBlankForScannedPageReturns422_noSilentLoss() throws Exception {
        when(ocrGateway.extractPages(any())).thenAnswer(inv -> {
            List<OcrPageRequest> reqs = inv.getArgument(0);
            return reqs.stream().map(r -> new OcrPageResult(r.pageNumber(), "")).toList();
        });
        mockMvc.perform(multipart("/checks")
                        .file(invoicePart("invoice-compliant-scanned.pdf"))
                        .param("lc", lc("SWIFT_MT700_Sample_Compliant.mt700")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("UNPROCESSABLE_ENTITY"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("no text for page")));
    }

    @Test
    void unknownRunIdReturns404() throws Exception {
        mockMvc.perform(get("/checks/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    private static void assertEquals(String expected, String actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
