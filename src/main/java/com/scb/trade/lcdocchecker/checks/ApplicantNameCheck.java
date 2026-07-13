package com.scb.trade.lcdocchecker.checks;

import com.scb.trade.lcdocchecker.domain.CheckResult;
import com.scb.trade.lcdocchecker.domain.Discrepancy;
import com.scb.trade.lcdocchecker.domain.InvoiceFields;
import com.scb.trade.lcdocchecker.domain.LcTerms;
import com.scb.trade.lcdocchecker.rulebook.RuleReference;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * UCP 600 Art. 18(a)(ii) — the invoice must be made out in the name of the applicant.
 * Uses the Jaccard guard; threshold {@code >= 0.85}.
 */
@Component
@Order(50)
public class ApplicantNameCheck implements DocumentCheck {

    static final String FIELD = "applicantName";
    static final String RULE = RuleReference.UCP_600_ART_18_A_II.ref();
    static final String DESCRIPTION =
            "The commercial invoice applicant name does not correspond to the applicant specified in the Letter of Credit.";

    @Override
    public String checkId() {
        return "applicant_rule";
    }

    @Override
    public CheckResult execute(LcTerms lc, InvoiceFields invoice) {
        if (invoice == null || invoice.applicantName() == null || invoice.applicantName().isBlank()) {
            return CheckResult.notApplicable(checkId(), "Invoice buyer/applicant name not extracted.");
        }
        if (!NameNormalizer.matches(lc.applicantName(), invoice.applicantName())) {
            Discrepancy d = Discrepancy.of(FIELD, lc.applicantName(), invoice.applicantName(), RULE, DESCRIPTION);
            return CheckResult.fail(checkId(), d);
        }
        return CheckResult.pass(checkId());
    }
}
