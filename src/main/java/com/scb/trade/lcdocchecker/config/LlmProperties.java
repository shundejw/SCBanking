package com.scb.trade.lcdocchecker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * LLM structured-extraction settings, bound from {@code lcchecker.llm.*}.
 */
@ConfigurationProperties(prefix = "lcchecker.llm")
public record LlmProperties(Duration timeout) {

    public Duration effectiveTimeout() {
        if (timeout != null && !timeout.isZero() && !timeout.isNegative()) {
            return timeout;
        }
        return Duration.ofSeconds(10);
    }
}
