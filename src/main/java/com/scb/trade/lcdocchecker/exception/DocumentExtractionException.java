package com.scb.trade.lcdocchecker.exception;

/**
 * Thrown when an invoice PDF cannot be loaded/rendered, or when text extraction and OCR
 * fallback together yield insufficient readable content. Maps to HTTP 422.
 */
public class DocumentExtractionException extends RuntimeException {
    public DocumentExtractionException(String message) {
        super(message);
    }

    public DocumentExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
