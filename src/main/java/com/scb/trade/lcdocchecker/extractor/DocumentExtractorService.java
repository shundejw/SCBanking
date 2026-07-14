package com.scb.trade.lcdocchecker.extractor;

import com.scb.trade.lcdocchecker.domain.DocumentType;
import com.scb.trade.lcdocchecker.domain.ExtractedDocument;
import com.scb.trade.lcdocchecker.exception.DocumentExtractionException;
import com.scb.trade.lcdocchecker.guard.UploadGuardService;
import com.scb.trade.lcdocchecker.util.FlowLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates the document extraction pipeline and dispatches to the right
 * {@link DocumentExtractor} by document type:
 * <ol>
 *   <li>upload guardrails (magic bytes, size),</li>
 *   <li>PDFBox text layer with OCR fallback ({@link PdfTextExtractor}),</li>
 *   <li>page-count guard,</li>
 *   <li>type-specific structured extraction → {@link ExtractedDocument}.</li>
 * </ol>
 */
@Service
public class DocumentExtractorService {

    private static final Logger log = LoggerFactory.getLogger(DocumentExtractorService.class);

    private final UploadGuardService guard;
    private final PdfTextExtractor pdfTextExtractor;
    private final List<DocumentExtractor<?>> extractors;

    public DocumentExtractorService(UploadGuardService guard,
                                    PdfTextExtractor pdfTextExtractor,
                                    List<DocumentExtractor<?>> extractors) {
        this.guard = guard;
        this.pdfTextExtractor = pdfTextExtractor;
        this.extractors = extractors;
    }

    public ExtractedDocument extract(byte[] pdfBytes, DocumentType documentType) {
        long startNs = System.nanoTime();
        FlowLog.info(log, DocumentExtractorService.class, "extract",
                "stage", "START", "pdfBytes", pdfBytes.length, "documentType", documentType);

        guard.validatePdf(pdfBytes);
        PdfTextExtractor.ExtractedPdf pdf = pdfTextExtractor.extract(pdfBytes);
        guard.enforcePageCount(pdf.pageCount());
        FlowLog.info(log, DocumentExtractorService.class, "extract",
                "stage", "STEP",
                "step", "extractPdfText",
                "textChars", pdf.text() == null ? 0 : pdf.text().length(),
                "pages", pdf.pageCount(),
                "ocrUsed", pdf.ocrUsed());

        DocumentExtractor<?> extractor = extractors.stream()
                .filter(e -> e.documentType() == documentType)
                .findFirst()
                .orElseThrow(() -> new DocumentExtractionException(
                        "No extractor registered for document type: " + documentType));
        ExtractedDocument doc = doExtract(extractor, pdf.text());
        if (doc.documentType() != documentType) {
            throw new DocumentExtractionException(
                    "Document type mismatch: requested " + documentType
                            + " but extractor produced " + doc.documentType());
        }
        FlowLog.info(log, DocumentExtractorService.class, "extract",
                "stage", "END",
                "result", "success",
                "documentType", doc.documentType(),
                "rawTextChars", doc.rawText() == null ? 0 : doc.rawText().length(),
                "costMs", elapsedMs(startNs));
        return doc;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ExtractedDocument doExtract(DocumentExtractor extractor, String text) {
        return (ExtractedDocument) extractor.extract(text);
    }

    private static long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }
}
