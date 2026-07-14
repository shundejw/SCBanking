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
 * ISBP 821 Preliminary Consideration (viii) — an absent/mistyped LC reference is NOT a
 * discrepancy unless the LC expressly requires it ({@code :46A:}/{@code :47A:}). Fires only
 * when the LC mandates the number AND the invoice fails to quote it.
 */
@Component
@Order(90)
public class LcReferenceCheck implements DocumentCheck<InvoiceFields> {

    static final String FIELD = "lcReferenceNumber";
    static final String RULE = RuleReference.ISBP_821_PRELIM_VIII.ref();
    static final String DESCRIPTION =
            "The LC expressly requires the documentary credit number on the commercial invoice, but it is missing.";

    @Override
    public String checkId() {
        return "lc_reference_rule";
    }

    @Override
    public Set<DocumentType> appliesTo() {
        return EnumSet.of(DocumentType.INVOICE);
    }

    @Override
    public CheckResult execute(LcTerms lc, InvoiceFields invoice) {
        if (!lc.requiresLcNumberOnInvoice()) {
            return CheckResult.notApplicable(checkId(), "LC does not require the credit number on the invoice.");
        }
        String presented = invoice == null ? null : invoice.lcReferenceNumber();
        if (!corresponds(lc.lcNumber(), presented)) {
            // presented_value is null when the invoice omits the number entirely.
            Discrepancy d = Discrepancy.of(FIELD, lc.lcNumber(),
                    (presented == null || presented.isBlank()) ? null : presented, RULE, DESCRIPTION);
            return CheckResult.fail(checkId(), d);
        }
        return CheckResult.pass(checkId());
    }

    private static boolean corresponds(String lcNumber, String presented) {
        if (presented == null || presented.isBlank()) {
            return false;
        }
        return normalize(lcNumber).equals(normalize(presented));
    }

    private static String normalize(String s) {
        return s.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }
}
