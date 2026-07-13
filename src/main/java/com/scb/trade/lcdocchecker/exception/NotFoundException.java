package com.scb.trade.lcdocchecker.exception;

/** Thrown when a check run or artifact is not found. Maps to HTTP 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
