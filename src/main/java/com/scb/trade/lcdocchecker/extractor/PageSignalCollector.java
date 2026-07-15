package com.scb.trade.lcdocchecker.extractor;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Collects {@link PageSignals} for one PDF page by combining pure text-layer statistics
 * ({@link PageTextStats}) with conservative PDF resource inspection.
 *
 * <p>{@code hasLargeRasterCandidate} is a deliberate WEAK proxy: it compares an image's native
 * pixel width to the page MediaBox width in POINTS (a unit mismatch — a full-page 300-DPI scan is
 * ~2480px vs ~595pt, so scans qualify; small logos do not). Only top-level image XObjects are
 * inspected; images nested inside Form XObjects are NOT recursed (documented MVP simplification).
 * It is a supporting signal only — the decider never OCRs a page solely because it has an image.
 *
 * <p>{@code mayHaveRenderableContent} is conservative: only a page with no content stream AND no
 * relevant resources returns {@code false}; everything else returns {@code true} (bias toward
 * never SKIPping a page that might have content).
 */
@Component
public class PageSignalCollector {

    public PageSignals collect(int pageNumber, String textLayer, PDPage page) {
        PageTextStats stats = PageTextStats.from(textLayer);
        boolean largeRaster = page != null && hasLargeRasterCandidate(page);
        boolean mayHaveContent = page != null && mayHaveRenderableContent(page);
        return new PageSignals(pageNumber, stats.nonWhitespaceChars(), stats.printableRatio(),
                stats.replacementCharRatio(), largeRaster, mayHaveContent);
    }

    private boolean hasLargeRasterCandidate(PDPage page) {
        PDResources res = page.getResources();
        if (res == null) {
            return false;
        }
        float pageWidth = page.getMediaBox().getWidth();
        for (COSName name : res.getXObjectNames()) {
            PDXObject xobject;
            try {
                xobject = res.getXObject(name);
            } catch (IOException e) {
                continue; // an unreadable resource must not mask other images
            }
            if (xobject instanceof PDImageXObject img && img.getWidth() >= pageWidth) {
                return true;
            }
        }
        return false;
    }

    private boolean mayHaveRenderableContent(PDPage page) {
        try {
            if (page.getContents() != null) {
                return true; // a content stream may draw text, vectors, or images
            }
        } catch (IOException e) {
            return true; // cannot read the content stream → assume content may exist (conservative)
        }
        PDResources res = page.getResources();
        if (res == null) {
            return false;
        }
        return res.getXObjectNames().iterator().hasNext()
                || res.getFontNames().iterator().hasNext()
                || res.getExtGStateNames().iterator().hasNext()
                || res.getColorSpaceNames().iterator().hasNext()
                || res.getPatternNames().iterator().hasNext();
    }
}
