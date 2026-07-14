package com.scb.trade.lcdocchecker.checks;

import com.scb.trade.lcdocchecker.domain.CheckResult;
import com.scb.trade.lcdocchecker.domain.Discrepancy;
import com.scb.trade.lcdocchecker.domain.DocumentType;
import com.scb.trade.lcdocchecker.domain.InvoiceFields;
import com.scb.trade.lcdocchecker.domain.LcTerms;
import com.scb.trade.lcdocchecker.rulebook.RuleReference;
import com.scb.trade.lcdocchecker.util.MoneyFormatter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.Set;

/**
 * UCP 600 Art. 18(b) — the invoice must not be drawn for more than the permitted credit
 * amount (LC amount + tolerance). All arithmetic uses {@link BigDecimal}.
 *
 * <p>Bank-practice wording: the description is objective/neutral (banks MAY accept an
 * over-amount invoice but cap honour at the credit amount) — never "non-compliant".
 *
 * <p>Output contract: {@code lc_value} = the LC's stated original amount, {@code presented_value}
 * = the invoice's stated amount (both currency-formatted); the derived tolerance ceiling and
 * explanation live in {@code description}.
 */
@Component
@Order(10)
public class AmountCheck implements DocumentCheck<InvoiceFields> {

    static final String FIELD = "invoice_amount";
    static final String RULE = RuleReference.UCP_600_ART_18_B.ref();

    @Override
    public String checkId() {
        return "amount_rule";
    }

    @Override
    public Set<DocumentType> appliesTo() {
        return EnumSet.of(DocumentType.INVOICE);
    }

    @Override
    public CheckResult execute(LcTerms lc, InvoiceFields invoice) {
        if (invoice == null || invoice.totalAmount() == null) {
            return CheckResult.notApplicable(checkId(), "Invoice total amount not extracted.");
        }
        BigDecimal presented = invoice.totalAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal ceiling = lc.amountCeiling();
        if (presented.compareTo(ceiling) > 0) {
            String lcValue = MoneyFormatter.format(lc.amount(), lc.currency());
            String presentedValue = MoneyFormatter.format(presented, invoice.currency());
            String description = "Invoice amount " + presentedValue
                    + " exceeds the LC amount " + lcValue
                    + " and the permitted tolerance drawing limits (max allowed "
                    + MoneyFormatter.format(ceiling, lc.currency()) + ").";
            Discrepancy d = Discrepancy.of(FIELD, lcValue, presentedValue, RULE, description);
            return CheckResult.fail(checkId(), d);
        }
        return CheckResult.pass(checkId());
    }
}
