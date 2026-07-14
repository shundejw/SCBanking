package com.scb.trade.lcdocchecker.checks;

import com.scb.trade.lcdocchecker.domain.CheckResult;
import com.scb.trade.lcdocchecker.domain.DocumentType;
import com.scb.trade.lcdocchecker.domain.InvoiceFields;
import com.scb.trade.lcdocchecker.domain.LcTerms;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * SIGNATURE_RULE — scans {@code :46A:}/{@code :47A:} for a signature requirement (e.g.
 * "signed commercial invoice"). A visual signature cannot be verified programmatically, so
 * the check returns {@link com.scb.trade.lcdocchecker.domain.CheckStatus#UNABLE}, routing
 * the document for manual compliance-officer review (surfaced as a warning, never a
 * discrepancy).
 */
@Component
@Order(40)
public class SignatureCheck implements DocumentCheck<InvoiceFields> {

    @Override
    public String checkId() {
        return "signature_rule";
    }

    @Override
    public Set<DocumentType> appliesTo() {
        return EnumSet.of(DocumentType.INVOICE);
    }

    @Override
    public CheckResult execute(LcTerms lc, InvoiceFields invoice) {
        if (lc.requiresSignedInvoice()) {
            return CheckResult.unable(checkId(),
                    "LC requires a signed commercial invoice; a visual signature cannot be verified "
                            + "programmatically and must be reviewed by a compliance officer.");
        }
        return CheckResult.pass(checkId());
    }
}
