package com.scb.trade.lcdocchecker.validation;

/**
 * Aggregated extraction-quality status, derived from findings by
 * {@link ExtractionValidation#of(java.util.List)}: any ERROR → FAIL, else any WARNING → WARN,
 * else OK.
 */
public enum ValidationStatus {
    OK,
    WARN,
    FAIL
}
