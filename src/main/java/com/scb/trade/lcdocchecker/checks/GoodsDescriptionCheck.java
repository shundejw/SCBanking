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
 * UCP 600 Art. 18(c) — the goods description in the invoice must CORRESPOND with that in
 * the credit (not necessarily mirror it). Correspondence is assessed at the token level:
 * the descriptions correspond when their token sets are equal, when one is a subset of the
 * other (invoice may omit detail, or add non-conflicting detail per ISBP 821 C3), or when
 * their Jaccard similarity is high.
 */
@Component
@Order(60)
public class GoodsDescriptionCheck implements DocumentCheck<InvoiceFields> {

    static final String FIELD = "goodsDescription";
    static final String RULE = RuleReference.UCP_600_ART_18_C.ref();
    static final String DESCRIPTION =
            "The description of goods in the commercial invoice does not correspond with that appearing in the Letter of Credit.";

    private static final double CORRESPOND_THRESHOLD = 0.90;

    @Override
    public String checkId() {
        return "goods_description_rule";
    }

    @Override
    public Set<DocumentType> appliesTo() {
        return EnumSet.of(DocumentType.INVOICE);
    }

    @Override
    public CheckResult execute(LcTerms lc, InvoiceFields invoice) {
        if (lc.goodsDescription() == null || lc.goodsDescription().isBlank()
                || invoice == null || invoice.goodsDescription() == null || invoice.goodsDescription().isBlank()) {
            return CheckResult.notApplicable(checkId(), "Goods description not present on LC and/or invoice.");
        }
        Set<String> lcTokens = goodsTokens(lc.goodsDescription());
        Set<String> invTokens = goodsTokens(invoice.goodsDescription());

        boolean corresponds = lcTokens.equals(invTokens)
                || lcTokens.containsAll(invTokens)
                || invTokens.containsAll(lcTokens)
                || jaccard(lcTokens, invTokens) >= CORRESPOND_THRESHOLD
                || squeeze(lc.goodsDescription()).equals(squeeze(invoice.goodsDescription()));

        if (!corresponds) {
            Discrepancy d = Discrepancy.of(FIELD, lc.goodsDescription(), invoice.goodsDescription(), RULE, DESCRIPTION);
            return CheckResult.fail(checkId(), d);
        }
        return CheckResult.pass(checkId());
    }

    static Set<String> goodsTokens(String raw) {
        String cleaned = raw.toUpperCase().replaceAll("[^A-Z0-9]", " ").trim();
        return new java.util.LinkedHashSet<>(java.util.Arrays.asList(cleaned.split("\\s+")));
    }

    /**
     * Whitespace-agnostic fingerprint: uppercased with all non-alphanumeric characters removed.
     * OCR-scanned invoices frequently lose inter-word spaces (tokens concatenated); this lets
     * identical descriptions that differ only in whitespace/punctuation still correspond.
     */
    static String squeeze(String raw) {
        return raw == null ? "" : raw.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        Set<String> inter = new java.util.LinkedHashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new java.util.LinkedHashSet<>(a);
        union.addAll(b);
        return (double) inter.size() / (double) union.size();
    }
}
