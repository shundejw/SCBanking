package com.scb.trade.lcdocchecker.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Formats monetary amounts as "<CURRENCY> #,##0.00" (e.g. {@code "USD 57,500.00"}), matching
 * the discrepancy-report examples in the case-study spec.
 *
 * <p><b>Scope</b>: amount fields only. Do NOT use for quantity / date / reference-number /
 * goods-description — those keep their own raw representation. This is a presentation helper,
 * not a cross-type normalizer.
 *
 * <p>A new {@link DecimalFormat} is created per call (the class is not thread-safe).
 */
public final class MoneyFormatter {

    private MoneyFormatter() {}

    /** "{@code USD 57,500.00}" — currency prefix + US thousands separator + 2 decimals. */
    public static String format(BigDecimal amount, String currency) {
        if (amount == null) return null;
        String prefix = (currency == null || currency.isBlank()) ? "" : currency.toUpperCase() + " ";
        DecimalFormat fmt = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return prefix + fmt.format(amount);
    }
}
