package com.scb.trade.lcdocchecker.extractor;

/**
 * Abstraction over the OCR sidecar so the PDF text extractor can depend on it (and tests
 * can substitute a deterministic stub). Implementations must NOT silently return empty on
 * failure — throw, so the caller can degrade loudly.
 */
public interface OcrGateway {

    /** Run OCR over the supplied PDF bytes and return the recognised text (may be empty). */
    String extract(byte[] pdfBytes);
}
