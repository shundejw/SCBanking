package com.scb.trade.lcdocchecker.checks;

import com.scb.trade.lcdocchecker.domain.CheckResult;
import com.scb.trade.lcdocchecker.domain.Discrepancy;
import com.scb.trade.lcdocchecker.domain.DocumentType;
import com.scb.trade.lcdocchecker.domain.InvoiceFields;
import com.scb.trade.lcdocchecker.domain.LcTerms;
import com.scb.trade.lcdocchecker.rulebook.RuleReference;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * UCP 600 Art. 18(a)(iii) — the invoice currency must be identical to the credit currency.
 */
@Component
@Order(20)
public class CurrencyCheck implements DocumentCheck<InvoiceFields> {

    static final String FIELD = "currency";
    static final String RULE = RuleReference.UCP_600_ART_18_A_III.ref();
    static final String DESCRIPTION = "Currency mismatch: Invoice currency must be identical to LC currency.";

    @Override
    public String checkId() {
        return "currency_rule";
    }

    @Override
    public Set<DocumentType> appliesTo() {
        return EnumSet.of(DocumentType.INVOICE);
    }

    @Override
    public CheckResult execute(LcTerms lc, InvoiceFields invoice) {
        if (invoice == null || invoice.currency() == null || invoice.currency().isBlank()) {
            return CheckResult.notApplicable(checkId(), "Invoice currency not extracted.");
        }
        if (!lc.currency().equalsIgnoreCase(invoice.currency().trim())) {
            Discrepancy d = Discrepancy.of(FIELD, lc.currency(), invoice.currency().trim(), RULE, DESCRIPTION);
            return CheckResult.fail(checkId(), d);
        }
        return CheckResult.pass(checkId());
    }
}
