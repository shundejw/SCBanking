package com.scb.trade.lcdocchecker.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard error body: {@code {"error": "<CODE>", "message": "<detail>"}}.
 * Never leaks raw stack traces.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String error, String message) {
}
