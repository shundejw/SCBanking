package com.scb.trade.lcdocchecker.exception;

/**
 * Thrown when an uploaded file fails ingestion guardrails (wrong magic bytes, exceeds
 * size/page limits, LC text too long). Maps to HTTP 422.
 */
public class UploadRejectedException extends RuntimeException {
    public UploadRejectedException(String message) {
        super(message);
    }
}
