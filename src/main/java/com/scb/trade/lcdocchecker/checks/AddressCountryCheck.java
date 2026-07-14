package com.scb.trade.lcdocchecker.checks;

import com.scb.trade.lcdocchecker.domain.CheckResult;
import com.scb.trade.lcdocchecker.domain.Discrepancy;
import com.scb.trade.lcdocchecker.domain.DocumentType;
import com.scb.trade.lcdocchecker.domain.InvoiceFields;
import com.scb.trade.lcdocchecker.domain.LcTerms;
import com.scb.trade.lcdocchecker.rulebook.RuleReference;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * UCP 600 Art. 14(j) — applicant and beneficiary addresses need not match exactly, but the
 * invoice addresses must be in the SAME COUNTRY as their respective LC addresses.
 *
 * <p>The check is per-party and only fires when BOTH the LC and the invoice state an address
 * for that party; an absent invoice address is treated as NOT_APPLICABLE (never a discrepancy).
 * Countries are compared by the last non-blank token of the address (e.g. "GERMANY", "SINGAPORE").
 *
 * <p>The fixture matrix does not exercise this check (no invoice addresses are provided), so it
 * is NOT_APPLICABLE for every fixture case; it is included for completeness and to demonstrate
 * the modular {@link DocumentCheck} SPI (requirement: "easy extension of rules and checks").
 */
@Component
@Order(55)
public class AddressCountryCheck implements DocumentCheck<InvoiceFields> {

    static final String RULE = RuleReference.UCP_600_ART_14_J.ref();
    static final String DESCRIPTION_APPLICANT =
            "The applicant address country on the invoice does not match the country in the Letter of Credit.";
    static final String DESCRIPTION_BENEFICIARY =
            "The beneficiary address country on the invoice does not match the country in the Letter of Credit.";

    @Override
    public String checkId() {
        return "address_country_rule";
    }

    @Override
    public Set<DocumentType> appliesTo() {
        return EnumSet.of(DocumentType.INVOICE);
    }

    @Override
    public CheckResult execute(LcTerms lc, InvoiceFields invoice) {
        if (invoice == null) {
            return CheckResult.notApplicable(checkId(), "Invoice fields not available.");
        }
        List<Discrepancy> found = new ArrayList<>(2);

        checkCountry(lc.applicantAddress(), invoice.applicantAddress(),
                "applicant_address", DESCRIPTION_APPLICANT, lc.applicantAddress(), invoice.applicantAddress(), found);
        checkCountry(lc.beneficiaryAddress(), invoice.sellerAddress(),
                "beneficiary_address", DESCRIPTION_BENEFICIARY, lc.beneficiaryAddress(), invoice.sellerAddress(), found);

        if (found.isEmpty()) {
            return CheckResult.notApplicable(checkId(), "Address countries match or invoice addresses not stated.");
        }
        // Multiple party discrepancies are not part of the fixture contract; report the first.
        return CheckResult.fail(checkId(), found.get(0));
    }

    private void checkCountry(String lcAddress, String invoiceAddress, String field,
                              String description, String lcValue, String presentedValue,
                              List<Discrepancy> found) {
        if (lcAddress == null || lcAddress.isBlank() || invoiceAddress == null || invoiceAddress.isBlank()) {
            return;
        }
        if (!countryOf(lcAddress).equals(countryOf(invoiceAddress))) {
            found.add(Discrepancy.of(field, lcValue, presentedValue, RULE, description));
        }
    }

    private static String countryOf(String address) {
        if (address == null) {
            return "";
        }
        String[] tokens = address.toUpperCase().replaceAll("[^A-Z ]", " ").trim().split("\\s+");
        return tokens.length == 0 ? "" : tokens[tokens.length - 1];
    }
}
