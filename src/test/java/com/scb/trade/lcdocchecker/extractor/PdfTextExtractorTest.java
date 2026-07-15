package com.scb.trade.lcdocchecker.extractor;

import com.scb.trade.lcdocchecker.config.PageAnalysisProperties;
import com.scb.trade.lcdocchecker.exception.DocumentExtractionException;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies per-page extraction against the real fixture PDFs. OCR is stubbed so the suite stays
 * deterministic (no live sidecar).
 */
class PdfTextExtractorTest {

    private static final Path INV_DIR = Path.of("docs/test_fixtures/invoices");

    private static final String COMPLIANT_TEXT = """
            SELLER / BENEFICIARY  XYZ EXPORT CO., LTD.
            BUYER / APPLICANT     ABC IMPORTERS PTE LTD
            Currency: USD
            Description of Goods: 100 METRIC TONS OF REFINED SUGAR, INCOTERMS 2020 CIF HAMBURG
            Total Invoice Value   USD 57,500.00
            """;

    private byte[] read(String name) throws Exception {
        return Files.readAllBytes(INV_DIR.resolve(name));
    }

    private PdfTextExtractor newExtractor(OcrGateway gateway) {
        PageSignalCollector collector = new PageSignalCollector();
        PageExtractionDecider decider = new PageExtractionDecider(new PageAnalysisProperties(null, null, null));
        return new PdfTextExtractor(collector, decider, gateway);
    }

    @Test
    void digitalPdfExtractsTextLayerWithoutOcr() throws Exception {
        AtomicInteger ocrCalls = new AtomicInteger();
        OcrGateway stub = reqs -> {
            ocrCalls.incrementAndGet();
            return List.of();
        };
        PdfTextExtractor extractor = newExtractor(stub);

        PdfTextExtractor.ExtractedPdf out = extractor.extract(read("invoice-compliant-digital.pdf"));

        assertTrue(out.text().contains("XYZ EXPORT CO., LTD."), () -> "text=" + out.text());
        assertFalse(out.ocrUsed());
        assertEquals(0, ocrCalls.get(), "OCR must NOT be invoked for a digital text-layer PDF");
    }

    @Test
    void scannedPdfFallsBackToOcr() throws Exception {
        AtomicInteger ocrCalls = new AtomicInteger();
        OcrGateway stub = reqs -> {
            ocrCalls.incrementAndGet();
            return reqs.stream().map(r -> new OcrPageResult(r.pageNumber(), COMPLIANT_TEXT)).toList();
        };
        PdfTextExtractor extractor = newExtractor(stub);

        PdfTextExtractor.ExtractedPdf out = extractor.extract(read("invoice-compliant-scanned.pdf"));

        assertTrue(out.ocrUsed(), "scanned PDF must trigger OCR fallback");
        assertEquals(1, ocrCalls.get(), "OCR must be invoked exactly once (single batched call)");
        assertTrue(out.text().contains("REFINED SUGAR"));
    }

    @Test
    void corruptPdfThrowsUnreadableMessage() throws Exception {
        byte[] pdf = read("invoice-unreadable.pdf");
        OcrGateway stub = reqs -> List.of();
        PdfTextExtractor extractor = newExtractor(stub);

        DocumentExtractionException ex = assertThrows(DocumentExtractionException.class,
                () -> extractor.extract(pdf));
        assertEquals(
                "PDF could not be rendered for text extraction or OCR; the document may be corrupt, encrypted, or unsupported.",
                ex.getMessage());
    }

    @Test
    void ocrBlankForPage_failsLoudly_noSilentLoss() throws Exception {
        byte[] pdf = read("invoice-compliant-scanned.pdf");
        AtomicInteger ocrCalls = new AtomicInteger();
        OcrGateway stub = reqs -> {
            ocrCalls.incrementAndGet();
            return reqs.stream().map(r -> new OcrPageResult(r.pageNumber(), "")).toList();
        };
        PdfTextExtractor extractor = newExtractor(stub);

        DocumentExtractionException ex = assertThrows(DocumentExtractionException.class,
                () -> extractor.extract(pdf));
        assertTrue(ex.getMessage().contains("no text for page"), ex.getMessage());
        assertEquals(1, ocrCalls.get());
    }
}
