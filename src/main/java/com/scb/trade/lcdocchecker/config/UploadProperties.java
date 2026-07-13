package com.scb.trade.lcdocchecker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ingestion guardrail thresholds. Bound from the {@code lcchecker.upload.*} namespace
 * (see application.yml).
 */
@ConfigurationProperties(prefix = "lcchecker.upload")
public record UploadProperties(
        long maxFileSizeMb,
        int maxPagesAllowed,
        String allowedMagicBytes,
        int maxLcTextLengthChars) {

    /** Hex magic bytes decoded to a byte array, e.g. "25504446" → {0x25,0x50,0x44,0x46}. */
    public byte[] magicBytes() {
        String hex = allowedMagicBytes == null ? "" : allowedMagicBytes.trim();
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseUnsignedInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
