package com.scb.trade.lcdocchecker.extractor;

import com.scb.trade.lcdocchecker.config.OcrProperties;
import com.scb.trade.lcdocchecker.exception.DocumentExtractionException;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the dual-stage extraction against the real fixture PDFs. OCR is stubbed so the
 * suite stays deterministic (no live sidecar).
 */
class PdfTextExtractorTest {

    private static final Path INV_DIR = Path.of("docs/test_fixtures/invoices");
    private static final OcrProperties PROPS =
            new OcrProperties(0.80, 100, "http://localhost:8000/api/v1/ocr", Duration.ofSeconds(30),
                    new OcrProperties.Paddle("http://localhost:8866/predict/ocr_system"));

    private byte[] read(String name) throws Exception {
        return Files.readAllBytes(INV_DIR.resolve(name));
    }

    @Test
    void digitalPdfExtractsTextLayerWithoutOcr() throws Exception {
        AtomicInteger ocrCalls = new AtomicInteger();
        OcrGateway stub = bytes -> {
            ocrCalls.incrementAndGet();
            return "should-not-be-used";
        };
        PdfTextExtractor extractor = new PdfTextExtractor(PROPS, stub);

        PdfTextExtractor.ExtractedPdf out = extractor.extract(read("invoice-compliant-digital.pdf"));

        assertTrue(out.text().contains("XYZ EXPORT CO., LTD."), () -> "text=" + out.text());
        assertFalse(out.ocrUsed());
        assertEquals(0, ocrCalls.get(), "OCR must NOT be invoked for a digital text-layer PDF");
    }

    @Test
    void scannedPdfFallsBackToOcr() throws Exception {
        AtomicInteger ocrCalls = new AtomicInteger();
        // OCR stub returns the compliant invoice text (sufficient length).
        OcrGateway stub = bytes -> {
            ocrCalls.incrementAndGet();
            return """
                    SELLER / BENEFICIARY  XYZ EXPORT CO., LTD.
                    BUYER / APPLICANT     ABC IMPORTERS PTE LTD
                    Currency: USD
                    Description of Goods: 100 METRIC TONS OF REFINED SUGAR, INCOTERMS 2020 CIF HAMBURG
                    Total Invoice Value   USD 57,500.00
                    """;
        };
        PdfTextExtractor extractor = new PdfTextExtractor(PROPS, stub);

        PdfTextExtractor.ExtractedPdf out = extractor.extract(read("invoice-compliant-scanned.pdf"));

        assertTrue(out.ocrUsed(), "scanned PDF must trigger OCR fallback");
        assertEquals(1, ocrCalls.get(), "OCR must be invoked exactly once");
        assertTrue(out.text().contains("REFINED SUGAR"));
    }

    @Test
    void corruptPdfThrowsUnreadableMessage() throws Exception {
        OcrGateway stub = bytes -> "unused";
        PdfTextExtractor extractor = new PdfTextExtractor(PROPS, stub);

        DocumentExtractionException ex = assertThrows(DocumentExtractionException.class,
                () -> extractor.extract(read("invoice-unreadable.pdf")));
        // Exact fixture wording.
        assertEquals(
                "PDF could not be rendered for text extraction or OCR; the document may be corrupt, encrypted, or unsupported.",
                ex.getMessage());
    }

    @Test
    void ocrInsufficientThrowsSpecificMessage() {
        AtomicInteger ocrCalls = new AtomicInteger();
        OcrGateway stub = bytes -> {
            ocrCalls.incrementAndGet();
            return "x"; // far below the 100-char threshold
        };
        PdfTextExtractor extractor = new PdfTextExtractor(PROPS, stub);

        // A scanned PDF whose OCR returns almost nothing.
        DocumentExtractionException ex = assertThrows(DocumentExtractionException.class,
                () -> extractor.extract(read("invoice-compliant-scanned.pdf")));
        assertEquals(
                "PDF text extraction and OCR fallback did not yield sufficient readable content.",
                ex.getMessage());
        assertEquals(1, ocrCalls.get());
    }
}
