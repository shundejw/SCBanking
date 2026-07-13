package com.scb.trade.lcdocchecker.checks;

import com.scb.trade.lcdocchecker.domain.CheckResult;
import com.scb.trade.lcdocchecker.domain.Discrepancy;
import com.scb.trade.lcdocchecker.domain.InvoiceFields;
import com.scb.trade.lcdocchecker.domain.LcTerms;
import com.scb.trade.lcdocchecker.rulebook.RuleReference;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UCP 600 Art. 30 quantity-tolerance check (goal2 §4.5, v2.2 §4.6.2).
 *
 * <p>The invoice carries no structured quantity field, so the quantity is read from the goods
 * description only when it begins with an unambiguous {@code <number> <unit>} token, where the
 * unit is the one the parser already extracted into {@link LcTerms#quantityUnit()}. When the
 * quantity or its premise cannot be stably identified, the check returns NOT_APPLICABLE rather
 * than making an arbitrary threshold comparison (v2.2 §4.6.2: 若无法稳定识别数量单位或数量前提，
 * 默认不做武断阈值比较).
 *
 * <p>The tolerance band reuses the LC's own {@code :39A:} band ({@link LcTerms#tolerancePlus()}
 * / {@link LcTerms#toleranceMinus()}, already zeroed by the parser when {@code :39B:} caps the
 * drawing). When no tolerance is declared, an exact match is required.
 */
@Component
@Order(95)
public class QuantityCheck implements DocumentCheck {

    private static final String FIELD = "quantity";
    private static final String RULE = RuleReference.UCP_600_ART_30_B.ref();
    private static final String DESCRIPTION =
            "The presented quantity does not fall within the tolerance permitted by the LC terms.";

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)");

    @Override
    public String checkId() {
        return "quantity_rule";
    }

    @Override
    public CheckResult execute(LcTerms lc, InvoiceFields invoice) {
        if (lc == null || invoice == null
                || lc.goodsDescription() == null || lc.goodsDescription().isBlank()
                || invoice.goodsDescription() == null || invoice.goodsDescription().isBlank()) {
            return CheckResult.notApplicable(checkId(), "Goods description missing on LC or invoice.");
        }
        String unit = lc.quantityUnit();
        if (unit == null || unit.isBlank()) {
            return CheckResult.notApplicable(checkId(), "LC quantity unit not stably identified.");
        }
        BigDecimal lcQty = leadingNumber(lc.goodsDescription()).orElse(null);
        if (lcQty == null) {
            return CheckResult.notApplicable(checkId(), "LC quantity not stably identified from goods description.");
        }
        BigDecimal invQty = leadingQuantityWithUnit(invoice.goodsDescription(), unit).orElse(null);
        if (invQty == null) {
            return CheckResult.notApplicable(checkId(),
                    "Invoice quantity not stably identified with a matching unit; no arbitrary comparison made.");
        }
        BigDecimal plus = lc.tolerancePlus() == null ? BigDecimal.ZERO : lc.tolerancePlus();
        BigDecimal minus = lc.toleranceMinus() == null ? BigDecimal.ZERO : lc.toleranceMinus();
        BigDecimal upper = lcQty.multiply(BigDecimal.ONE.add(plus.divide(HUNDRED, 6, RoundingMode.HALF_UP)));
        BigDecimal lower = lcQty.multiply(BigDecimal.ONE.subtract(minus.divide(HUNDRED, 6, RoundingMode.HALF_UP)));
        if (invQty.compareTo(lower) >= 0 && invQty.compareTo(upper) <= 0) {
            return CheckResult.pass(checkId());
        }
        Discrepancy d = Discrepancy.of(FIELD,
                lcQty.toPlainString() + " " + unit,
                invQty.toPlainString() + " " + unit,
                RULE, DESCRIPTION);
        return CheckResult.fail(checkId(), d);
    }

    /** Leading {@code <number>} token of a goods description, or empty if it does not start with a number. */
    private static Optional<BigDecimal> leadingNumber(String text) {
        Matcher m = LEADING_NUMBER.matcher(text);
        if (!m.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(m.group(1)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Leading {@code <number> <unit>} in the invoice, or empty if the unit does not match the LC unit. */
    private static Optional<BigDecimal> leadingQuantityWithUnit(String text, String unit) {
        Pattern p = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*" + Pattern.quote(unit) + "\\b",
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (!m.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(m.group(1)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
