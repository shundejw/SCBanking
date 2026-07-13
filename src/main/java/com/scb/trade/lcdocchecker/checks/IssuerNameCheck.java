package com.scb.trade.lcdocchecker.checks;

import com.scb.trade.lcdocchecker.domain.CheckResult;
import com.scb.trade.lcdocchecker.domain.Discrepancy;
import com.scb.trade.lcdocchecker.domain.InvoiceFields;
import com.scb.trade.lcdocchecker.domain.LcTerms;
import com.scb.trade.lcdocchecker.rulebook.RuleReference;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * UCP 600 Art. 18(a)(i) — the invoice must appear to be issued by the beneficiary named
 * in the credit. Uses the Jaccard guard (no {@code contains()}); threshold {@code >= 0.85}.
 */
@Component
@Order(30)
public class IssuerNameCheck implements DocumentCheck {

    static final String FIELD = "issuerName";
    static final String RULE = RuleReference.UCP_600_ART_18_A_I.ref();
    static final String DESCRIPTION =
            "The commercial invoice issuer name does not match the beneficiary specified in the Letter of Credit.";

    @Override
    public String checkId() {
        return "issuer_rule";
    }

    @Override
    public CheckResult execute(LcTerms lc, InvoiceFields invoice) {
        if (invoice == null || invoice.sellerName() == null || invoice.sellerName().isBlank()) {
            return CheckResult.notApplicable(checkId(), "Invoice seller/beneficiary name not extracted.");
        }
        if (!NameNormalizer.matches(lc.beneficiaryName(), invoice.sellerName())) {
            Discrepancy d = Discrepancy.of(FIELD, lc.beneficiaryName(), invoice.sellerName(), RULE, DESCRIPTION);
            return CheckResult.fail(checkId(), d);
        }
        return CheckResult.pass(checkId());
    }
}
