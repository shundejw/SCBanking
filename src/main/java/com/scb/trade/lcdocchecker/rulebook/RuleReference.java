package com.scb.trade.lcdocchecker.rulebook;

/**
 * Single source of truth for every UCP 600 / ISBP 821 citation emitted by the rule engine
 * (goal2 §2.6, §4.7). Checks must never inline legal reference strings; they pick a constant
 * here and call {@link #ref()} for the discrepancy's {@code rule_reference} field.
 *
 * <p>The citation strings are pinned to the fixture contract
 * ({@code docs/test_fixtures/expected-results.json}); changing a value here changes every
 * discrepancy that cites it, so edits must be deliberate.
 */
public enum RuleReference {

    /** Data in a document need not be identical to, but must not conflict with, other documents / the credit. */
    UCP_600_ART_14_D("UCP 600 Art. 14(d)"),

    /** Applicant/beneficiary addresses need not match the LC, but must be in the same country. */
    UCP_600_ART_14_J("UCP 600 Art. 14(j)"),

    /** A commercial invoice must appear to have been issued by the beneficiary. */
    UCP_600_ART_18_A_I("UCP 600 Art. 18(a)(i)"),

    /** A commercial invoice must be made out in the name of the applicant. */
    UCP_600_ART_18_A_II("UCP 600 Art. 18(a)(ii)"),

    /** A commercial invoice must be in the same currency as the credit. */
    UCP_600_ART_18_A_III("UCP 600 Art. 18(a)(iii)"),

    /** A nominated bank may accept an invoice issued for an amount in excess of that permitted by the credit. */
    UCP_600_ART_18_B("UCP 600 Art. 18(b)"),

    /** The goods/services description in the invoice must correspond with that in the credit. */
    UCP_600_ART_18_C("UCP 600 Art. 18(c)"),

    /** "about"/"approximately" qualifies amount or quantity — tolerance not exceeding 10% more or less. */
    UCP_600_ART_30_A("UCP 600 Art. 30(a)"),

    /** Quantity tolerance not exceeding 5% more or 5% less, when the conditions of 30(b) are met. */
    UCP_600_ART_30_B("UCP 600 Art. 30(b)"),

    /** Even without partial shipments, up to 5% less than the credit amount is permitted under 30(c). */
    UCP_600_ART_30_C("UCP 600 Art. 30(c)"),

    /** When the credit expressly requires the credit number on the invoice, its absence is a discrepancy. */
    ISBP_821_PRELIM_VIII("ISBP 821 Preliminary Consideration (viii) / LC :46A");

    private final String ref;

    RuleReference(String ref) {
        this.ref = ref;
    }

    /** The exact citation string to place in a discrepancy's {@code rule_reference} field. */
    public String ref() {
        return ref;
    }
}
