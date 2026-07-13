package com.scb.trade.lcdocchecker.extractor;

import com.scb.trade.lcdocchecker.domain.InvoiceExtractedData;
import com.scb.trade.lcdocchecker.domain.InvoiceFields;
import com.scb.trade.lcdocchecker.guard.UploadGuardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the invoice extraction pipeline:
 * <ol>
 *   <li>upload guardrails (magic bytes, size),</li>
 *   <li>PDFBox text layer with OCR fallback ({@link PdfTextExtractor}),</li>
 *   <li>page-count guard,</li>
 *   <li>Spring AI structured extraction → {@link InvoiceFields}.</li>
 * </ol>
 */
@Service
public class DocumentExtractorService {

    private static final Logger log = LoggerFactory.getLogger(DocumentExtractorService.class);

    private final UploadGuardService guard;
    private final PdfTextExtractor pdfTextExtractor;
    private final InvoiceExtractionService invoiceExtractionService;

    public DocumentExtractorService(UploadGuardService guard,
                                    PdfTextExtractor pdfTextExtractor,
                                    InvoiceExtractionService invoiceExtractionService) {
        this.guard = guard;
        this.pdfTextExtractor = pdfTextExtractor;
        this.invoiceExtractionService = invoiceExtractionService;
    }

    public InvoiceFields extract(byte[] pdfBytes) {
        guard.validatePdf(pdfBytes);
        PdfTextExtractor.ExtractedPdf pdf = pdfTextExtractor.extract(pdfBytes);
        guard.enforcePageCount(pdf.pageCount());
        log.info("Invoice text extracted ({} chars, {} pages, OCR={})",
                pdf.text() == null ? 0 : pdf.text().length(), pdf.pageCount(), pdf.ocrUsed());

        InvoiceExtractedData data = invoiceExtractionService.extract(pdf.text());
        return data.toFields(pdf.text());
    }
}
