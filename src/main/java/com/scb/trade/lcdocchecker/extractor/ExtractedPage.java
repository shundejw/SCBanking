package com.scb.trade.lcdocchecker.extractor;

/**
 * Per-page extraction result: 1-based page number, the acquired text ({@code ""} for a SKIP page),
 * and the strategy that produced it. SKIP pages are retained (never deleted from the result) so
 * downstream tooling can reconstruct which page was skipped and why.
 */
public record ExtractedPage(int pageNumber, String text, ExtractionSource source) {
    public ExtractedPage {
        text = text == null ? "" : text;
    }
}
