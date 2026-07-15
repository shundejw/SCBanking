package com.scb.trade.lcdocchecker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-page text-usability thresholds for {@code lcchecker.pdf.page-analysis.*}. Boxed types so
 * missing YAML entries resolve to documented defaults rather than {@code 0}. Thresholds are
 * inclusive (a value equal to the threshold passes) and should be calibrated on real samples.
 */
@ConfigurationProperties(prefix = "lcchecker.pdf.page-analysis")
public record PageAnalysisProperties(
        Integer minNonWhitespaceChars,
        Double minPrintableRatio,
        Double maxReplacementCharRatio) {

    public PageAnalysisProperties {
        if (minNonWhitespaceChars == null || minNonWhitespaceChars < 0) {
            minNonWhitespaceChars = 100;
        }
        if (minPrintableRatio == null || minPrintableRatio < 0.0 || minPrintableRatio > 1.0) {
            minPrintableRatio = 0.85;
        }
        if (maxReplacementCharRatio == null || maxReplacementCharRatio < 0.0 || maxReplacementCharRatio > 1.0) {
            maxReplacementCharRatio = 0.05;
        }
    }
}
