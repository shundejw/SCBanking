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

/**
 * UCP 600 Art. 18(b) — the invoice must not be drawn for more than the permitted credit
 * amount (LC amount + tolerance). All arithmetic uses {@link BigDecimal}.
 *
 * <p>Bank-practice wording: the description is objective/neutral (banks MAY accept an
 * over-amount invoice but cap honour at the credit amount) — never "non-compliant".
 */
@Component
@Order(10)
public class AmountCheck implements DocumentCheck {

    static final String FIELD = "totalAmount";
    static final String RULE = RuleReference.UCP_600_ART_18_B.ref();
    static final String DESCRIPTION =
            "The invoice amount exceeds the maximum tolerance drawing limits allowed by LC terms.";

    @Override
    public String checkId() {
        return "amount_rule";
    }

    @Override
    public CheckResult execute(LcTerms lc, InvoiceFields invoice) {
        if (invoice == null || invoice.totalAmount() == null) {
            return CheckResult.notApplicable(checkId(), "Invoice total amount not extracted.");
        }
        BigDecimal presented = invoice.totalAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal ceiling = lc.amountCeiling();
        if (presented.compareTo(ceiling) > 0) {
            Discrepancy d = Discrepancy.of(
                    FIELD,
                    "Max Allowed: " + ceiling.toPlainString(),
                    presented.toPlainString(),
                    RULE,
                    DESCRIPTION);
            return CheckResult.fail(checkId(), d);
        }
        return CheckResult.pass(checkId());
    }
}
