package com.scb.trade.lcdocchecker.exception;

/**
 * Thrown when an MT700 message is malformed or missing a mandatory field.
 * Maps to HTTP 422 {@code UNPROCESSABLE_ENTITY} with a descriptive message.
 */
public class InvalidMt700Exception extends RuntimeException {
    public InvalidMt700Exception(String message) {
        super(message);
    }
}
