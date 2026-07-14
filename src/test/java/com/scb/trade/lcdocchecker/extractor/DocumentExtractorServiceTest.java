package com.scb.trade.lcdocchecker.extractor;

import com.scb.trade.lcdocchecker.domain.DocumentType;
import com.scb.trade.lcdocchecker.domain.ExtractedDocument;
import com.scb.trade.lcdocchecker.exception.DocumentExtractionException;
import com.scb.trade.lcdocchecker.guard.UploadGuardService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the dispatch + consistency contract of {@link DocumentExtractorService}:
 * the requested document type must match a registered extractor, and the produced
 * document's {@link ExtractedDocument#documentType()} must match the request (fail-fast).
 */
class DocumentExtractorServiceTest {

    private static final byte[] PDF_BYTES = {0x25, 0x50, 0x44, 0x46}; // "%PDF"

    @SuppressWarnings("rawtypes")
    @Test
    void extractorProducingMismatchedDocumentTypeFailsFast() {
        ExtractedDocument mismatched = new ExtractedDocument() {
            @Override public DocumentType documentType() { return null; }
            @Override public String rawText() { return "irrelevant"; }
        };
        DocumentExtractor extractor = mock(DocumentExtractor.class);
        when(extractor.documentType()).thenReturn(DocumentType.INVOICE);
        when(extractor.extract(anyString())).thenReturn(mismatched);

        UploadGuardService guard = mock(UploadGuardService.class);
        PdfTextExtractor pdf = mock(PdfTextExtractor.class);
        when(pdf.extract(any())).thenReturn(new PdfTextExtractor.ExtractedPdf("text", 1, false));

        DocumentExtractorService svc = new DocumentExtractorService(guard, pdf, List.of(extractor));

        DocumentExtractionException ex = assertThrows(DocumentExtractionException.class,
                () -> svc.extract(PDF_BYTES, DocumentType.INVOICE));
        assertTrue(ex.getMessage().contains("mismatch"), ex.getMessage());
    }

    @Test
    void noExtractorRegisteredForTypeThrows() {
        UploadGuardService guard = mock(UploadGuardService.class);
        PdfTextExtractor pdf = mock(PdfTextExtractor.class);
        when(pdf.extract(any())).thenReturn(new PdfTextExtractor.ExtractedPdf("text", 1, false));
        DocumentExtractorService svc = new DocumentExtractorService(guard, pdf, List.of());

        assertThrows(DocumentExtractionException.class,
                () -> svc.extract(PDF_BYTES, DocumentType.INVOICE));
    }
}
