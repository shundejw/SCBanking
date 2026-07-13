package com.scb.trade.lcdocchecker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * OCR fallback settings, bound from {@code lcchecker.ocr.*}.
 */
@ConfigurationProperties(prefix = "lcchecker.ocr")
public record OcrProperties(
        double confidenceThreshold,
        int minTextLengthThreshold,
        String sidecarUrl,
        Duration timeout,
        Paddle paddle) {

    public record Paddle(String url) {
    }
}
