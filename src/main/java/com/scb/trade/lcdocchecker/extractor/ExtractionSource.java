package com.scb.trade.lcdocchecker.extractor;

/**
 * Per-page text-acquisition strategy decided by {@link PageExtractionDecider}.
 *
 * <ul>
 *   <li>{@link #TEXT} — use the PDFBox text layer for this page.</li>
 *   <li>{@link #OCR} — render the page and send the image to the OCR sidecar.</li>
 *   <li>{@link #SKIP} — confirmed no renderable content (blank page); no OCR performed.</li>
 * </ul>
 */
public enum ExtractionSource {
    TEXT,
    OCR,
    SKIP
}
