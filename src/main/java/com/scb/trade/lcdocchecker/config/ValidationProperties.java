package com.scb.trade.lcdocchecker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Field-validation guardrail settings, bound from {@code lcchecker.validation.*}.
 * All fields default safely so no YAML entry is required for the guardrail to run.
 */
@ConfigurationProperties(prefix = "lcchecker.validation")
public record ValidationProperties(
        String zoneId,
        Duration allowedFutureSkew,
        int invoiceNumberMaxLength,
        List<String> invoiceDatePatterns) {

    public ValidationProperties {
        if (zoneId == null || zoneId.isBlank()) {
            zoneId = "UTC";
        }
        if (allowedFutureSkew == null || allowedFutureSkew.isNegative()) {
            allowedFutureSkew = Duration.ofDays(7);
        }
        if (invoiceNumberMaxLength <= 0) {
            invoiceNumberMaxLength = 100;
        }
        if (invoiceDatePatterns == null || invoiceDatePatterns.isEmpty()) {
            invoiceDatePatterns = List.of("yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", "dd-MMM-yyyy", "d MMM yyyy", "yyyyMMdd");
        } else {
            invoiceDatePatterns = List.copyOf(invoiceDatePatterns);
        }
    }
}
