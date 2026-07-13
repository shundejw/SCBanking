package com.scb.trade.lcdocchecker.parser;

import com.scb.trade.lcdocchecker.domain.LcTerms;
import com.scb.trade.lcdocchecker.exception.InvalidMt700Exception;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Programmatic SWIFT MT700 parser (no LLM). Extracts Block 4 via a small state machine,
 * validates the mandatory field set, and maps the tags into {@link LcTerms}.
 *
 * <p>Handles both field shapes present in the fixtures: tag-with-inline-value
 * ({@code :50:ABC IMPORTERS PTE LTD}) and tag-on-own-line ({@code :50:\nEURO IMPORT...}).
 */
@Service
public class LcParserService {

    /** Tags that must be present on any valid MT700 (claude-code-goal.md Phase 0.2). */
    private static final List<String> MANDATORY_TAGS =
            List.of("20", "27", "40A", "31C", "40E", "31D", "50", "59", "32B", "41A", "49");

    private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
            Map.entry("20", "Documentary Credit Number"),
            Map.entry("27", "Sequence of Total"),
            Map.entry("40A", "Form of Documentary Credit"),
            Map.entry("31C", "Date of Issue"),
            Map.entry("40E", "Applicable Rules"),
            Map.entry("31D", "Date and Place of Expiry"),
            Map.entry("50", "Applicant"),
            Map.entry("59", "Beneficiary"),
            Map.entry("32B", "Currency Code, Amount"),
            Map.entry("41A", "Available With...By..."),
            Map.entry("49", "Confirmation Instructions"));

    private static final Pattern TAG_LINE = Pattern.compile("^:(\\d{2}[A-Z]?):\\s?(.*)$");
    private static final Pattern QTY_UNIT = Pattern.compile(
            "(?i)(\\d+(?:\\.\\d+)?)\\s*(METRIC\\s+TONS?|MT|UNITS?|KGS?|KILOS?|KILOGRAMS?|PIECES?|PCS?|CARTONS?|BOXES?|LITRES?|LITERS?|METERS?|METRES?)");

    public LcTerms parse(String mt700) {
        if (mt700 == null || mt700.isBlank()) {
            throw new InvalidMt700Exception("MT700 text is empty.");
        }
        Map<String, String> fields = parseBlock4(mt700);
        validateMandatory(fields);

        String currency;
        BigDecimal amount;
        try {
            String raw32B = fields.get("32B");
            currency = raw32B.substring(0, 3);
            String amt = raw32B.substring(3).replace(",", ".").trim();
            amount = new BigDecimal(amt);
        } catch (Exception e) {
            throw new InvalidMt700Exception(
                    "mandatory field 32B (Currency Code, Amount) is malformed: " + e.getMessage());
        }

        BigDecimal[] tol = parseTolerance(fields.get("39A"));
        BigDecimal tolerancePlus = tol[0];
        BigDecimal toleranceMinus = tol[1];
        boolean notExceeding = isNotExceeding(fields.get("39B"));
        if (notExceeding) {
            tolerancePlus = BigDecimal.ZERO;
        }

        String[] applicant = splitParty(fields.get("50"));
        String[] beneficiary = splitParty(fields.get("59"));

        return new LcTerms(
                trimLine(fields.get("20")),
                trimLine(fields.get("27")),
                trimLine(fields.get("40A")),
                trimLine(fields.get("31C")),
                trimLine(fields.get("40E")),
                trimLine(fields.get("31D")),
                applicant[0],
                applicant[1],
                beneficiary[0],
                beneficiary[1],
                currency,
                amount,
                tolerancePlus,
                toleranceMinus,
                notExceeding,
                firstNonBlankLine(fields.get("41A")),
                trim(fields.get("44E")),
                trim(fields.get("44F")),
                trim(fields.get("45A")),
                trim(fields.get("46A")),
                trim(fields.get("47A")),
                trimLine(fields.get("49")),
                extractQuantityUnit(fields.get("45A")));
    }

    /** Extract Block 4 (the text between {@code {4:} and {@code -}}) and split into tag→value. */
    private Map<String, String> parseBlock4(String mt700) {
        int start = mt700.indexOf("{4:");
        if (start < 0) {
            throw new InvalidMt700Exception("MT700 Block 4 ({4: ... -}) is missing.");
        }
        int contentStart = start + 3;
        int end = mt700.indexOf("-}", contentStart);
        String block = end < 0
                ? mt700.substring(contentStart)
                : mt700.substring(contentStart, end);

        Map<String, String> fields = new LinkedHashMap<>();
        String currentTag = null;
        StringBuilder value = new StringBuilder();
        for (String line : block.split("\\r?\\n")) {
            Matcher m = TAG_LINE.matcher(line);
            if (m.matches()) {
                flush(fields, currentTag, value);
                currentTag = m.group(1);
                value = new StringBuilder(m.group(2));
            } else if (currentTag != null) {
                if (value.length() > 0) {
                    value.append('\n');
                }
                value.append(line);
            }
        }
        flush(fields, currentTag, value);
        return fields;
    }

    private void flush(Map<String, String> fields, String tag, StringBuilder value) {
        if (tag != null) {
            fields.put(tag, normalizeValue(value.toString()));
        }
    }

    private void validateMandatory(Map<String, String> fields) {
        // 32B is the critical amount field; report it first when absent (matches fixture contract).
        if (!hasAmountField(fields)) {
            throw new InvalidMt700Exception("mandatory field 32B (" + FIELD_LABELS.get("32B") + ") is missing.");
        }
        for (String tag : MANDATORY_TAGS) {
            if ("32B".equals(tag)) {
                continue;
            }
            boolean present = fields.containsKey(tag)
                    || ("41A".equals(tag) && (fields.containsKey("41A") || fields.containsKey("41D")));
            if (!present) {
                throw new InvalidMt700Exception(
                        "mandatory field " + tag + " (" + FIELD_LABELS.getOrDefault(tag, "") + ") is missing.");
            }
        }
    }

    private boolean hasAmountField(Map<String, String> fields) {
        return fields.containsKey("32B");
    }

    private BigDecimal[] parseTolerance(String tag39A) {
        if (tag39A == null || tag39A.isBlank()) {
            return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
        }
        // SWIFT :39A: may be "5/5", "05/05", or "PCT 05/05" — strip a leading PCT qualifier.
        String normalized = tag39A.trim()
                .replaceFirst("(?i)^PCT\\s+", "")
                .replaceAll("\\s+", "");
        String[] parts = normalized.split("/");
        if (parts.length != 2) {
            return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
        }
        try {
            return new BigDecimal[]{
                    new BigDecimal(parts[0].trim()),
                    new BigDecimal(parts[1].trim())};
        } catch (NumberFormatException e) {
            return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
        }
    }

    private boolean isNotExceeding(String tag39B) {
        if (tag39B == null || tag39B.isBlank()) {
            return false;
        }
        String u = tag39B.toUpperCase();
        return u.contains("NOT EXCEEDING") || u.contains("MAXIMUM");
    }

    /** @return {name (first non-blank line), address (remaining lines joined by ", ")}. */
    private String[] splitParty(String raw) {
        if (raw == null || raw.isBlank()) {
            return new String[]{"", ""};
        }
        List<String> lines = new ArrayList<>();
        for (String l : raw.split("\\r?\\n")) {
            if (!l.isBlank()) {
                lines.add(l.trim());
            }
        }
        if (lines.isEmpty()) {
            return new String[]{"", ""};
        }
        String name = lines.get(0);
        String address = lines.size() > 1 ? String.join(", ", lines.subList(1, lines.size())) : "";
        return new String[]{name, address};
    }

    private String extractQuantityUnit(String goods) {
        if (goods == null || goods.isBlank()) {
            return "";
        }
        Matcher m = QTY_UNIT.matcher(goods);
        if (m.find()) {
            return m.group(2).replaceAll("\\s+", " ").toUpperCase().trim();
        }
        return "";
    }

    private static String normalizeValue(String v) {
        if (v == null) {
            return "";
        }
        // strip leading newlines left by tag-on-own-line fields; trim trailing whitespace.
        return v.replaceAll("^\\n+", "").stripTrailing();
    }

    private static String trim(String v) {
        return v == null ? null : normalizeValue(v);
    }

    private static String trimLine(String v) {
        return v == null ? null : v.trim();
    }

    private static String firstNonBlankLine(String v) {
        if (v == null || v.isBlank()) {
            return v == null ? null : "";
        }
        for (String l : v.split("\\r?\\n")) {
            if (!l.isBlank()) {
                return l.trim();
            }
        }
        return "";
    }
}
