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
 * UCP 600 Art. 14(d) — data in the invoice must not conflict with the credit. Compares the
 * stated port of loading (LC {@code :44E:} vs invoice) only when BOTH state a port; an
 * omitted optional port is never a discrepancy. Reported under the isolated field
 * {@code portOfLoading}.
 */
@Component
@Order(70)
public class PortOfLoadingCheck implements DocumentCheck<InvoiceFields> {

    static final String FIELD = "portOfLoading";
    static final String RULE = RuleReference.UCP_600_ART_14_D.ref();
    static final String DESCRIPTION = "The stated loading port conflicts with the credit.";

    @Override
    public String checkId() {
        return "port_of_loading_rule";
    }

    @Override
    public Set<DocumentType> appliesTo() {
        return EnumSet.of(DocumentType.INVOICE);
    }

    @Override
    public CheckResult execute(LcTerms lc, InvoiceFields invoice) {
        if (lc.portOfLoading() == null || lc.portOfLoading().isBlank()
                || invoice == null || invoice.portOfLoading() == null || invoice.portOfLoading().isBlank()) {
            return CheckResult.notApplicable(checkId(), "Port of loading not stated on both LC and invoice.");
        }
        if (!normalize(lc.portOfLoading()).equals(normalize(invoice.portOfLoading()))) {
            Discrepancy d = Discrepancy.of(FIELD, lc.portOfLoading(), invoice.portOfLoading(), RULE, DESCRIPTION);
            return CheckResult.fail(checkId(), d);
        }
        return CheckResult.pass(checkId());
    }

    private static String normalize(String s) {
        return s.toUpperCase().replaceAll("[^A-Z0-9]", " ").replaceAll("\\s+", " ").trim();
    }
}
