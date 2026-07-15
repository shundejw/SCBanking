package com.scb.trade.lcdocchecker.extractor;

/** One page image to be OCR'd. {@code imageBytes} is the rendered page (e.g. PNG). */
public record OcrPageRequest(int pageNumber, byte[] imageBytes) {
}
