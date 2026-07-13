package com.scb.trade.lcdocchecker.extractor;

import com.scb.trade.lcdocchecker.config.OcrProperties;
import com.scb.trade.lcdocchecker.exception.DocumentExtractionException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Dual-stage invoice text extractor. Stage 1 reads the embedded text layer with Apache
 * PDFBox. If the non-whitespace content is below {@code lcchecker.ocr.min-text-length-threshold}
 * (default 100), stage 2 falls back to the OCR sidecar. A corrupt/unsupported PDF and an
 * OCR result that is still insufficient both raise {@link DocumentExtractionException}.
 */
@Component
public class PdfTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfTextExtractor.class);

    private final OcrProperties props;
    private final OcrGateway ocrGateway;

    public PdfTextExtractor(OcrProperties props, OcrGateway ocrGateway) {
        this.props = props;
        this.ocrGateway = ocrGateway;
    }

    public ExtractedPdf extract(byte[] pdfBytes) {
        PDDocument doc;
        try {
            doc = Loader.loadPDF(pdfBytes);
        } catch (IOException e) {
            throw new DocumentExtractionException(
                    "PDF could not be rendered for text extraction or OCR; the document may be corrupt, encrypted, or unsupported.",
                    e);
        }
        int pageCount;
        String digitalText;
        try {
            pageCount = doc.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            digitalText = stripper.getText(doc);
        } catch (IOException e) {
            throw new DocumentExtractionException(
                    "PDF could not be rendered for text extraction or OCR; the document may be corrupt, encrypted, or unsupported.",
                    e);
        } finally {
            try {
                doc.close();
            } catch (IOException ignored) {
                // best-effort close
            }
        }

        if (nonWhitespaceLength(digitalText) >= props.minTextLengthThreshold()) {
            return new ExtractedPdf(digitalText.trim(), pageCount, false);
        }

        // Scanned / image-only PDF → OCR fallback.
        log.info("PDF text layer insufficient ({} non-ws chars); invoking OCR fallback.", nonWhitespaceLength(digitalText));
        String ocrText = ocrGateway.extract(pdfBytes);
        if (nonWhitespaceLength(ocrText) >= props.minTextLengthThreshold()) {
            return new ExtractedPdf(ocrText.trim(), pageCount, true);
        }
        throw new DocumentExtractionException(
                "PDF text extraction and OCR fallback did not yield sufficient readable content.");
    }

    private static int nonWhitespaceLength(String s) {
        return s == null ? 0 : s.replaceAll("\\s", "").length();
    }

    /** Result of PDF text extraction: text, page count, and whether OCR was used. */
    public record ExtractedPdf(String text, int pageCount, boolean ocrUsed) {
    }
}
