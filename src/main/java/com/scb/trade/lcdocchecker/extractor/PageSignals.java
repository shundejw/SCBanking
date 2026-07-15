package com.scb.trade.lcdocchecker.extractor;

/**
 * Per-page content signals collected by {@link PageSignalCollector} and consumed by
 * {@link PageExtractionDecider}. All ratios are computed over Unicode code points (not
 * char/code-unit, not ASCII-only) with a shared denominator = non-whitespace code points.
 *
 * @param pageNumber              1-based page index
 * @param nonWhitespaceChars      count of non-whitespace code points in the text layer
 * @param printableRatio          defined code points / non-whitespace code points (1.0 when none)
 * @param replacementCharRatio    U+FFFD count / non-whitespace code points (0.0 when none)
 * @param hasLargeRasterCandidate weak proxy: a page-sized raster exists (native px vs page pt; unit
 *                                mismatch documented — supporting signal only, never sole OCR trigger)
 * @param mayHaveRenderableContent conservative: only false when the page is confirmed to have no
 *                                content stream and no usable resources
 */
public record PageSignals(
        int pageNumber,
        int nonWhitespaceChars,
        double printableRatio,
        double replacementCharRatio,
        boolean hasLargeRasterCandidate,
        boolean mayHaveRenderableContent) {
}
