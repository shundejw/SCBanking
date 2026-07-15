package com.scb.trade.lcdocchecker.extractor;

/**
 * OCR result for one page. {@code text} may be empty; the caller (extractor) decides whether an
 * empty result for a page that should contain content is a hard failure (no silent page loss).
 */
public record OcrPageResult(int pageNumber, String text) {
    public OcrPageResult {
        text = text == null ? "" : text;
    }
}
