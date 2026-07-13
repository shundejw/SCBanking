package com.scb.trade.lcdocchecker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Intermediate-artifact store root. Default {@code docs/artifacts} → {@code <project>/docs/artifacts/{runId}}.
 */
@ConfigurationProperties(prefix = "lcchecker.artifact")
public record ArtifactProperties(String rootDir) {
}
