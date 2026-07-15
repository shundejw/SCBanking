package com.scb.trade.lcdocchecker.extractor;

import com.scb.trade.lcdocchecker.exception.DocumentExtractionException;
import com.scb.trade.lcdocchecker.util.FlowLog;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-page invoice text extractor. For each page it collects content signals, decides
 * TEXT/OCR/SKIP, acquires the text from exactly one source per page, and merges in page order.
 *
 * <p>Safety invariant (financial-document bias): a page is SKIPped only when it is confirmed to
 * have no renderable content; everything uncertain goes to OCR. Any page that should yield content
 * but fails (OCR missing the page, or OCR returning blank text) fails the WHOLE extraction — there
 * is no silent partial result. SKIP pages are retained in the merge as empty entries.
 */
@Component
public class PdfTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfTextExtractor.class);
    private static final String UNREADABLE_MSG =
            "PDF could not be rendered for text extraction or OCR; the document may be corrupt, encrypted, or unsupported.";
    private static final String INSUFFICIENT_MSG =
            "PDF text extraction and OCR fallback did not yield sufficient readable content.";
    private static final float OCR_DPI = 300f;

    private final PageSignalCollector signalCollector;
    private final PageExtractionDecider decider;
    private final OcrGateway ocrGateway;

    public PdfTextExtractor(PageSignalCollector signalCollector,
                            PageExtractionDecider decider,
                            OcrGateway ocrGateway) {
        this.signalCollector = signalCollector;
        this.decider = decider;
        this.ocrGateway = ocrGateway;
    }

    public ExtractedPdf extract(byte[] pdfBytes) {
        FlowLog.info(log, PdfTextExtractor.class, "extract",
                "stage", "START", "pdfBytes", pdfBytes.length);
        PDDocument doc;
        try {
            doc = Loader.loadPDF(pdfBytes);
        } catch (IOException e) {
            throw new DocumentExtractionException(UNREADABLE_MSG, e);
        }
        int pageCount;
        List<ExtractedPage> pages;
        try {
            pageCount = doc.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            PDFRenderer renderer = new PDFRenderer(doc);
            pages = processPages(doc, stripper, renderer);
        } catch (IOException e) {
            throw new DocumentExtractionException(UNREADABLE_MSG, e);
        } finally {
            try {
                doc.close();
            } catch (IOException ignored) {
                // best-effort close
            }
        }

        pages.sort(Comparator.comparingInt(ExtractedPage::pageNumber));
        String combined = joinPages(pages);
        boolean ocrUsed = pages.stream().anyMatch(p -> p.source() == ExtractionSource.OCR);
        FlowLog.info(log, PdfTextExtractor.class, "extract",
                "stage", "END", "pages", pageCount, "textChars", combined.length(), "ocrUsed", ocrUsed);
        if (combined.isBlank()) {
            throw new DocumentExtractionException(INSUFFICIENT_MSG);
        }
        return new ExtractedPdf(combined, pageCount, ocrUsed);
    }

    private List<ExtractedPage> processPages(PDDocument doc, PDFTextStripper stripper, PDFRenderer renderer)
            throws IOException {
        int pageCount = doc.getNumberOfPages();
        List<ExtractedPage> pages = new ArrayList<>(pageCount);
        List<OcrPageRequest> ocrRequests = new ArrayList<>();
        for (int i = 0; i < pageCount; i++) {
            int pageNumber = i + 1;
            PDPage page = doc.getPage(i);
            String textLayer = stripPage(stripper, doc, pageNumber);
            PageSignals signals = signalCollector.collect(pageNumber, textLayer, page);
            ExtractionSource source = decider.decide(signals);
            FlowLog.info(log, PdfTextExtractor.class, "extract",
                    "stage", "STEP", "page", pageNumber, "source", source,
                    "nonWs", signals.nonWhitespaceChars(),
                    "printableRatio", signals.printableRatio(),
                    "replacementRatio", signals.replacementCharRatio(),
                    "largeRaster", signals.hasLargeRasterCandidate(),
                    "mayHaveContent", signals.mayHaveRenderableContent());
            switch (source) {
                case TEXT -> pages.add(new ExtractedPage(pageNumber, textLayer, ExtractionSource.TEXT));
                case SKIP -> pages.add(new ExtractedPage(pageNumber, "", ExtractionSource.SKIP));
                case OCR -> ocrRequests.add(new OcrPageRequest(pageNumber, renderPage(renderer, i)));
            }
        }
        acquireOcrPages(ocrRequests, pages);
        return pages;
    }

    private void acquireOcrPages(List<OcrPageRequest> ocrRequests, List<ExtractedPage> pages) {
        if (ocrRequests.isEmpty()) {
            return;
        }
        List<OcrPageResult> ocrResults = ocrGateway.extractPages(ocrRequests);
        Map<Integer, String> textByPage = new HashMap<>();
        for (OcrPageResult r : ocrResults) {
            textByPage.put(r.pageNumber(), r.text());
        }
        for (OcrPageRequest req : ocrRequests) {
            String text = textByPage.get(req.pageNumber());
            if (text == null) {
                throw new DocumentExtractionException("OCR did not return a result for page " + req.pageNumber()
                        + "; refusing to proceed (no silent page loss).");
            }
            if (text.isBlank()) {
                throw new DocumentExtractionException("OCR returned no text for page " + req.pageNumber()
                        + "; treating as extraction failure (no silent page loss).");
            }
            pages.add(new ExtractedPage(req.pageNumber(), text, ExtractionSource.OCR));
        }
    }

    private static String stripPage(PDFTextStripper stripper, PDDocument doc, int pageNumber) throws IOException {
        stripper.setStartPage(pageNumber);
        stripper.setEndPage(pageNumber);
        return stripper.getText(doc);
    }

    private static byte[] renderPage(PDFRenderer renderer, int pageIndex) throws IOException {
        BufferedImage img = renderer.renderImageWithDPI(pageIndex, OCR_DPI);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    private static String joinPages(List<ExtractedPage> pages) {
        StringBuilder sb = new StringBuilder();
        for (ExtractedPage p : pages) {
            if (p.text().isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(p.text());
        }
        return sb.toString();
    }

    /** Result of PDF text extraction: text, page count, and whether OCR was used. */
    public record ExtractedPdf(String text, int pageCount, boolean ocrUsed) {
    }
}
