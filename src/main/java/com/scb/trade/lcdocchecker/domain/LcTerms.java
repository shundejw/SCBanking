package com.scb.trade.lcdocchecker.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Immutable representation of the LC terms parsed from a SWIFT MT700 message.
 *
 * <p>All monetary and tolerance values use {@link BigDecimal}; the permitted drawing band
 * is derived deterministically from {@code :32B:} and {@code :39A:}/{@code :39B:}.
 */
public record LcTerms(
        String lcNumber,            // :20:
        String sequenceOfTotal,     // :27:
        String form,                // :40A:
        String issueDate,           // :31C:  YYMMDD
        String applicableRules,     // :40E:
        String expiryDateAndPlace,  // :31D:
        String applicantName,       // :50:  first line
        String applicantAddress,    // :50:  remaining lines
        String beneficiaryName,     // :59:  first line
        String beneficiaryAddress,  // :59:  remaining lines
        String currency,            // :32B: ISO-4217
        BigDecimal amount,          // :32B:
        BigDecimal tolerancePlus,   // :39A: plus percent (0 if absent or :39B: caps)
        BigDecimal toleranceMinus,  // :39A: minus percent
        boolean notExceeding,       // :39B: contains NOT EXCEEDING / MAXIMUM
        String availableWithBy,     // :41a:
        String portOfLoading,       // :44E:
        String portOfDischarge,     // :44F:
        String goodsDescription,    // :45A:
        String documentsRequired,   // :46A:
        String additionalConditions,// :47A:
        String confirmationInstructions, // :49:
        String quantityUnit) {      // unit token extracted from :45A: (e.g. METRIC TONS)

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** Maximum permitted drawing amount = amount × (1 + tolerancePlus/100). Scale 2. */
    public BigDecimal amountCeiling() {
        BigDecimal plus = notExceeding ? BigDecimal.ZERO : tolerancePlus;
        return amount.multiply(BigDecimal.ONE.add(plus.divide(HUNDRED, 6, RoundingMode.HALF_UP)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Minimum permitted drawing amount = amount × (1 − toleranceMinus/100). Scale 2. */
    public BigDecimal amountFloor() {
        return amount.multiply(BigDecimal.ONE.subtract(toleranceMinus.divide(HUNDRED, 6, RoundingMode.HALF_UP)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Whether the LC expressly requires the invoice to quote the documentary credit number.
     * Scanned in {@code :46A:} or {@code :47A:} (UCP/ISBP: omission is not a discrepancy
     * unless the LC mandates it).
     */
    public boolean requiresLcNumberOnInvoice() {
        String hay = combine(documentsRequired, additionalConditions);
        if (hay == null || hay.isBlank()) {
            return false;
        }
        String u = hay.toUpperCase();
        return u.contains("CREDIT NUMBER") || u.contains("L/C NUMBER")
                || u.contains("DOCUMENTARY CREDIT NUMBER") || u.contains("LC NUMBER");
    }

    /** Whether the LC requires a signed commercial invoice ({@code :46A:}/{@code :47A:}). */
    public boolean requiresSignedInvoice() {
        String hay = combine(documentsRequired, additionalConditions);
        if (hay == null || hay.isBlank()) {
            return false;
        }
        String u = hay.toUpperCase();
        return u.contains("SIGNED") && u.contains("INVOICE");
    }

    private static String combine(String a, String b) {
        StringBuilder sb = new StringBuilder();
        if (a != null) sb.append(a);
        if (b != null) sb.append(' ').append(b);
        return sb.toString();
    }
}
