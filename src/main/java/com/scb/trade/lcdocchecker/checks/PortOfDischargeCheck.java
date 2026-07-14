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
 * UCP 600 Art. 14(d) — port of discharge comparison (LC {@code :44F:} vs invoice). Reported
 * under the isolated field {@code portOfDischarge}.
 */
@Component
@Order(80)
public class PortOfDischargeCheck implements DocumentCheck<InvoiceFields> {

    static final String FIELD = "portOfDischarge";
    static final String RULE = RuleReference.UCP_600_ART_14_D.ref();
    static final String DESCRIPTION = "The stated discharge port conflicts with the credit.";

    @Override
    public String checkId() {
        return "port_of_discharge_rule";
    }

    @Override
    public Set<DocumentType> appliesTo() {
        return EnumSet.of(DocumentType.INVOICE);
    }

    @Override
    public CheckResult execute(LcTerms lc, InvoiceFields invoice) {
        if (lc.portOfDischarge() == null || lc.portOfDischarge().isBlank()
                || invoice == null || invoice.portOfDischarge() == null || invoice.portOfDischarge().isBlank()) {
            return CheckResult.notApplicable(checkId(), "Port of discharge not stated on both LC and invoice.");
        }
        if (!normalize(lc.portOfDischarge()).equals(normalize(invoice.portOfDischarge()))) {
            Discrepancy d = Discrepancy.of(FIELD, lc.portOfDischarge(), invoice.portOfDischarge(), RULE, DESCRIPTION);
            return CheckResult.fail(checkId(), d);
        }
        return CheckResult.pass(checkId());
    }

    private static String normalize(String s) {
        return s.toUpperCase().replaceAll("[^A-Z0-9]", " ").replaceAll("\\s+", " ").trim();
    }
}
