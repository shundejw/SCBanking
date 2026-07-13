package com.scb.trade.lcdocchecker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Active rule allowlist, bound from {@code lcchecker.rules.enabled}. When the list is
 * absent or empty, ALL registered checks run.
 */
@ConfigurationProperties(prefix = "lcchecker.rules")
public record RulesProperties(List<String> enabled) {
}
