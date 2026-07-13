package com.scb.trade.lcdocchecker.checks;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Word-level name matching using Jaccard similarity (the "Jaccard guard").
 *
 * <p>Deliberately does NOT use {@code String.contains()} or substring checks (which are
 * trivially spoofed). Names are uppercased, accent-folded, stripped of legal designators
 * (LTD, CO, INC, PTE, GMBH, …) and punctuation, then compared as token sets. A strict
 * threshold of {@code >= 0.85} is required for buyer/seller names.
 */
public final class NameNormalizer {

    /** Legal-entity designators stripped before comparison. */
    private static final Set<String> DESIGNATORS = Set.of(
            "LTD", "LIMITED", "CO", "COMPANY", "INC", "INCORPORATED", "CORP", "CORPORATION",
            "PTE", "PTY", "LLC", "LLP", "PLC", "GMBH", "AG", "SA", "SARL", "BV", "NV",
            "SDN", "BHD", "OYJ", "OY", "AB", "AS", "SPA", "SRL", "KG", "PC", "LP");

    public static final double THRESHOLD = 0.85;

    private NameNormalizer() {
    }

    /** @return word-level Jaccard similarity in [0,1]; 1.0 when both names normalise to empty. */
    public static double similarity(String a, String b) {
        Set<String> sa = tokens(a);
        Set<String> sb = tokens(b);
        if (sa.isEmpty() && sb.isEmpty()) {
            return 1.0;
        }
        Set<String> intersection = new LinkedHashSet<>(sa);
        intersection.retainAll(sb);
        Set<String> union = new LinkedHashSet<>(sa);
        union.addAll(sb);
        return (double) intersection.size() / (double) union.size();
    }

    public static boolean matches(String a, String b) {
        return similarity(a, b) >= THRESHOLD;
    }

    static Set<String> tokens(String raw) {
        if (raw == null || raw.isBlank()) {
            return new LinkedHashSet<>();
        }
        // accent-fold (NFKD + strip non-ASCII-ish diacritics), uppercase
        String folded = Normalizer.normalize(raw, Normalizer.Form.NFKD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        // letters/digits/spaces only
        String cleaned = folded.toUpperCase().replaceAll("[^A-Z0-9]", " ").trim();
        Set<String> tokens = new LinkedHashSet<>();
        for (String t : cleaned.split("\\s+")) {
            if (!t.isEmpty() && !DESIGNATORS.contains(t)) {
                tokens.add(t);
            }
        }
        return tokens;
    }
}
