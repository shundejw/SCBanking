# Official Rules Reference — SWIFT FIN MT700, UCP 600, ISBP 821 (English)

## 5.1 Document notes

**Purpose.** This document is an **independent baseline of official rules** — SWIFT FIN MT700 field rules, UCP 600 articles, and ISBP 821 paragraphs — compiled to enable a later assessment of whether an LC/invoice compliance checker correctly implements the official requirements. It is derived **only from official / authoritative public sources**; no project source code, tests, comments, constants, or fixtures were read or used to derive its content, so it cannot have been "pre-contaminated" by the existing implementation.

**Scope.** Limited to the documentary-credit and commercial-invoice examination scenarios relevant to this project: MT700 message structure and the specific fields listed in §5.2; UCP 600 Articles 14, 18, 30, 31; ISBP 821 Preliminary Considerations and Section C (invoices).

**Disclaimer & copyright boundary.** UCP 600 (ICC Publication 600), ISBP 821 (ICC Publication 821, 2023 revision), and the SWIFT MT standard are **copyrighted and paywalled**. This document:
- is **not legal advice** and **does not replace** the official ICC/SWIFT publications;
- cites **locators** (field tags, article numbers, paragraph numbers) — these are facts, not copyrightable expression;
- quotes only **≤1-sentence** snippets from public official pages;
- **paraphrases** all rule content in its own words;
- marks every rule with an **evidence confidence** and reproduces **no** large block of any official publication.

To confirm any rule *verbatim*, the paid ICC/SWIFT publications are required (see §5.6 and the copyright-boundary note at the end).

**Evidence confidence scale.**
- **Verified** — confirmed in an official-primary source (ICC / SWIFT public material).
- **Cross-verified** — confirmed in ≥2 credible sources, including at least one secondary, with no contradiction.
- **Unverified** — the locator (field tag / article / paragraph number) or the content could not be confirmed from a credible source; never fabricated.

**Sources consulted (all accessed 2026-07-12).**

| ID | Source | Publisher | Tier |
|----|--------|-----------|------|
| S1 | SWIFT Standards MT Category 7 — Advance Information, Nov 2019 (MT700 field specs) | SWIFT (official) | Official-primary |
| S2 | MT 700 Format Specifications, Nov 2018 | Nordea bank | Reputable secondary |
| S3 | ICC Banking Commission Technical Advisory Briefing No. 12 — "Read in Context" (sub-Art. 14(d)) | ICC (official) | Official-primary |
| S4 | Documentary Credits — Rules, Guidelines & Terminology | ICC Academy | Official-primary (ICC body) |
| S5 | ISBP Insights — Avoiding Common LC Discrepancies | ICC Academy | Official-primary (ICC body) |
| S6 | Set of Guidance Papers on Recommended Principles and Usages around UCP 600 Rules | ICC (official) | Official-primary |
| S7 | "Title of the Invoice" (ISBP 821 C1 analysis) | Trade Services Update | Reputable secondary |
| S8 | "When an Invoice Exceeds the LC Amount" (sub-Art. 18(b)) | mr Oldman (practitioner) | Reputable secondary |
| S9 | ICC Opinion TA784 | ICC (official) | Official-primary |

URLs:
- S1 https://www.swift.com/sites/default/files/documents/swift_solutions_sr2019_cat_7_advanceinformation.pdf
- S2 https://www.nordea.com/en/doc/mt-700-format-specifications.pdf
- S3 https://www.icc-austria.org/fxdata/iccws/prod/media/files/20241113_TABriefing_No_12_Read_in_Context.pdf
- S4 https://academy.iccwbo.org/international-trade/article/documentary-credits-rules-guidelines-terminology/
- S5 https://academy.iccwbo.org/trade-finance/article/isbp-insights-avoiding-common-lc-discrepancies/
- S6 https://iccwbo.org/wp-content/uploads/sites/3/2023/03/Set-of-Guidance-Papers-on-Recommended-Principles-and-Usages-around-UCP600-Rules.pdf
- S7 https://www.tradeservicesupdate.com/articles/title-of-the-invoice
- S8 https://mroldman.net/when-an-invoice-exceeds-the-lc-amount-understanding-ucp-600-sub-article-18b/
- S9 https://iccmex.mx/query/document/470ta784-documento.pdf

Additional secondary sources used **only** to cross-verify (never as sole authority): tradefinance.training, tradefinanceglobal.com (TFG), collyerconsulting.com, fgcapitaladvisors.com, traydstream.com, lcviews.com, Studocu course materials, AnyFlip, ovrseas.io, NNRV Trade Partners, and named practitioner posts on LinkedIn.

**Edition note.** S1 (SWIFT 2019 advance PDF) is the newest *public* SWIFT edition used here. The current 2026 SWIFT standard (behind the SWIFT paywall / MyStandards) may differ slightly. ISBP 821 is the ICC Publication 821, **2023 revision** (a further revision was underway as of Oct 2024). UCP 600 is ICC Publication 600 (2007, in force).

---

## 5.2 SWIFT FIN MT700 field rules table

**Important:** SWIFT field rules are **message-structure rules** (field tag, mandatory/optional, format, option letter, business meaning). They are **NOT** the same as UCP 600 document-examination rules; the two must not be conflated. A field's M/O status governs whether the MT700 *message* is valid, not whether an *invoice* is compliant.

Primary M/O authority: **S1** (SWIFT 2019 advance PDF). M = Mandatory, O = Optional, O* = Optional/conditional.

| Field | Field Name | Mandatory/Optional | Format / Option | Business Meaning | Source | Confidence |
|---|---|---|---|---|---|---|
| :27: | Sequence of Total | **M** | `1!n/1!n` | Position of this message within the MT700/MT701 series; for MT700 the first number is fixed at 1 | S1 | Verified |
| :40A: | Form of Documentary Credit | **M** | Option A `24x` | Credit type code: IRREVOCABLE / IRREVOCABLE TRANSFERABLE / IRREVOCABLE STANDBY / IRREVOC TRANS STANDBY | S1 | Verified |
| :20: | Documentary Credit Number | **M** | `16x` | LC reference assigned by the issuing bank (Sender); must not start/end with `/` or contain `//` | S1 | Verified |
| :23: | Reference to Pre-Advice | O | `16x` | If present, signals the credit was pre-advised; must begin with code `PREADV/` | S1 | Verified |
| :31C: | Date of Issue | **M** | Option C `6!n` (YYMMDD) | Date the issuing bank considers the credit issued | S1 | Verified |
| :40E: | Applicable Rules | **M** | Option E `30x[/35x]` | Governing rules code, e.g. `UCPURR LATEST VERSION`, `UCP LATEST VERSION`, `EUCP LATEST VERSION`, `ISP LATEST VERSION`, `OTHR` | S1 | Verified |
| :31D: | Date and Place of Expiry | **M** | Option D `6!n29x` | Expiry date + place where documents may be presented | S1 | Verified |
| :50: | Applicant | **M** | `4*35x` (plain :50:, **no option letter**) | Party on whose behalf the credit is issued (the buyer). Note: `:50F:`/`:50K:` are **not** MT700 tags | S1 | Verified |
| :59: | Beneficiary | **M** | `[/34x] 4*35x` (plain :59:) | Party in whose favour the credit is issued (the seller); optional account subfield | S1 | Verified |
| :32B: | Currency Code, Amount | **M** | Option B `3!a15d` | ISO-4217 currency + LC amount; decimal comma mandatory | S1 | Verified |
| :39A: | Percentage Credit Amount Tolerance | O | Option A `2n/2n` | +/- tolerance as a % of the credit amount (Tol1=+, Tol2=−). Relates to UCP 600 Art. 30 | S1 | Verified |
| :39B: | Maximum Credit Amount | **Unverified in S1** | — | **UNVERIFIED.** S1 (SWIFT 2019) does **not** list `:39B:` in the MT700 table; S2 (Nordea 2018) does. Sources disagree — do not assume `:39B:` exists in the current MT700 standard | S2 only | **Unverified** |
| :39C: | Additional Amounts Covered | O | Option C `4*35x` | Extra amounts available (insurance, freight, interest, etc.) | S1 | Verified |
| :41A: / :41D: | Available With … By … | **M** | A `4!a2!a2!c[3!c] 14x` (BIC) or D `4*35x 14x` (name/address) | Nominated bank + availability mode (BY PAYMENT / BY ACCEPTANCE / BY NEGOTIATION / BY DEF PAYMENT / BY MIXED PYMT); "Any bank" → option D | S1 | Verified |
| :42C: | Drafts at … | O* | Option C `3*35x` | Tenor of drafts; must co-exist with :42A:/:42D: (network rule C1) | S1 | Verified |
| :42P: | Negotiation/Deferred Payment Details | O* | Option P `4*35x` | Payment date / determination method for deferred-payment or negotiation-only credits | S1 | Verified |
| :43P: | Partial Shipments | O | Option P `11x` | Code ALLOWED / CONDITIONAL / NOT ALLOWED (mirrors UCP 600 Art. 31) | S1 | Verified |
| :43T: | Transhipment | O | Option T `11x` | Code ALLOWED / CONDITIONAL / NOT ALLOWED (UCP 600 Art. 32 territory) | S1 | Verified |
| :44A: | Place of Taking in Charge / Dispatch … / Place of Receipt | O | Option A `65x` | Multimodal taking-in-charge / road-rail-inland-waterway receipt / place of dispatch | S1 | Verified |
| :44E: | Port of Loading / Airport of Departure | O | Option E `65x` | Port/airport where goods are loaded. **Modern field** — took over the port-loading function of the pre-Nov-2009 :44A: | S1 | Verified |
| :44F: | Port of Discharge / Airport of Destination | O | Option F `65x` | Port/airport of discharge/destination. **Modern field** — took over the port-discharge function of the pre-Nov-2009 :44B: | S1 | Verified |
| :44B: | Place of Final Destination / For Transportation to … / Place of Delivery | O | Option B `65x` | Final destination / place of delivery | S1 | Verified |
| :44C: | Latest Date of Shipment | O* | Option C `6!n` | Latest date for loading/dispatch/taking in charge; mutually exclusive with :44D: (network rule C3) | S1 | Verified |
| :44D: | Shipment Period | O* | Option D `6*65x` | Period during which shipment may occur; mutually exclusive with :44C: | S1 | Verified |
| :45A: | Description of Goods and/or Services | O | Option A `100*65z` | Goods/services description + Incoterms; the invoice must correspond to this per Art. 18(c) / ISBP C3 | S1 | Verified |
| :46A: | Documents Required | O | Option A `100*65z` | The stipulated documents (e.g. "signed commercial invoice in 3 copies"); governs whether a signature / LC-number is required on the invoice | S1 | Verified |
| :47A: | Additional Conditions | O | Option A `100*65z` | Further conditions (e.g. "invoice must quote LC number"); can make an otherwise-optional invoice attribute mandatory | S1 | Verified |
| :48: | Period for Presentation in Days | O | `3n[/35x]` | Days after shipment for presentation. **Absence = 21 days** (SWIFT usage + UCP 600 Art. 14(c)) | S1 | Verified |
| :49: | Confirmation Instructions | **M** | `7!x` | Code CONFIRM / MAY ADD / WITHOUT | S1 | Verified |
| :71B: | (listed as "Details of Charges") | — | — | **NOT a valid MT700 field.** MT700 uses **:71D: Charges** (Option D `6*35z`, O). `:71B:` belongs to MT 768/769 (guarantees). Any spec using :71B: in an MT700/LC context is a mislabel of :71D: | S1 | Verified (the absence) |

**Mandatory fields in MT700 (11):** `:27:, :40A:, :20:, :31C:, :40E:, :31D:, :50:, :59:, :32B:, :41a:, :49:`. All others are Optional / conditional.

**Network-validated rules confirmed (S1):** C1 — :42C: and :42A:/:42D: must both be present when drafts are used; C2 — only one of (:42C:+:42a:) / :42M: / :42P:; C3 — :44C: XOR :44D: (not both).

**Modern-field replacements (relevant for auditing):**
- `:44E:` / `:44F:` (port of loading / port of discharge) replaced the port functions that `:44A:` / `:44B:` carried before the **November 2009 SWIFT Standards Release**. Post-2009: :44A: = receipt/taking in charge; :44E: = port of loading; :44F: = port of discharge; :44B: = final destination/delivery.
- `:50:` in MT700 has **no option letter**; `:50F:` / `:50K:` are not MT700 tags.
- `:40E:` (Applicable Rules) and `:40A:` (Form) coexist as distinct mandatory fields in MT700. (In MT 710/720 the single field :40B: combines form + confirmation status.)

**Field-count summary:** of the 30 tags in scope, **29 confirmed** with definitive M/O from S1; **1 unverified** (`:39B:` — sources disagree); **1 mislabelled** (`:71B:` is not an MT700 field; correct = `:71D:`).

---

## 5.3 UCP 600 article list

### Article 14 — Standard for Examination of Documents

```text
Article:                14
title:                  Standard for Examination of Documents
Paraphrased rule:       14(a) A bank examines a presentation by looking only at the documents on
                        their face to decide whether they appear to constitute a complying presentation.
                        14(b) The examination period is a maximum of five banking days after the day of
                        presentation (the former "reasonable time" standard was removed).
                        14(c) Documents must be presented within a maximum of 21 calendar days after
                        the date of shipment, and in any event by the expiry date — unless the credit
                        fixes a different period.
                        14(d) Data in a document, read in context with the credit, the document itself,
                        and international standard banking practice, NEED NOT BE IDENTICAL TO, BUT MUST
                        NOT CONFLICT WITH, data in that document, any other stipulated document, or the
                        credit.
                        14(e) In documents OTHER than the commercial invoice, the goods/services/
                        performance description, if stated, may be in general terms not conflicting with
                        the description in the credit.
                        14(f) For a document that is not a transport, insurance, or commercial-invoice
                        document, where the credit does not stipulate the issuer or data content, banks
                        accept it if its content appears to fulfil the function of the required document
                        and otherwise complies with 14(d).
                        14(j) Beneficiary and applicant addresses appearing in any stipulated document
                        need not be the same as in the credit or other documents, but must be in the
                        SAME COUNTRY as the respective credit addresses; contact details (email/phone/
                        fax) may be disregarded. Exception: where the applicant's address/contact appears
                        as consignee/notify party on a transport document, it must be as stated in the
                        credit.
Applicability condition: Applies to every presentation examined under a UCP-600 credit (selected via
                        :40E: code UCPURR/UCP LATEST VERSION).
Relation to invoice/MT700: 14(d) is the master consistency rule between invoice data and the LC
                        (:45A:, :50:, :59:, :32B:). 14(e) is why only the invoice must carry the full
                        goods description (pairs with Art. 18(c)). 14(c) interacts with :48: (absence ⇒
                        21-day default) and :31D: (expiry). 14(j) governs party address checks vs :50:/:59:.
Source(s):              S3 (14(d) verbatim, official-primary); S4 (14(a),(b),(c),(j)); S5 (14(e),(f)); S6.
Evidence confidence:    14(a),(b),(c),(d),(j) = Verified; 14(e),(f) = Cross-verified.
```

**14(d) short verbatim quote (official, ≤1 sentence, S3):** *"data in a document, when read in context with the credit, the document itself and international standard banking practice, need not be identical to, but must not conflict with, data in that document, any other stipulated document or the credit."* — ICC Banking Commission TAB Briefing No. 12.

**Known divergence:** TAB 12 (S3) stresses that "read in context with" is often misapplied — some banks historically raised discrepancies for mere non-identical data (the old UCP 500 "inconsistency" rule) instead of genuine conflict. The modern standard is narrower: only a true *conflict* is a discrepancy.

### Article 18 — Commercial Invoice

```text
Article:                18
title:                  Commercial Invoice
Paraphrased rule:       18(a) A commercial invoice must: appear on its face to be the invoice named in
                        the credit; appear to be issued by the beneficiary; be made out in the name of
                        the applicant (except as provided in Art. 38 transferable credits); and be made
                        out in the same currency as the credit.
                          18(a)(i) issued by the beneficiary.
                          18(a)(ii) made out in the name of the applicant (subject to Art. 38).
                          18(a)(iii) in the same currency as the credit.
                        18(b) A bank MAY ACCEPT a commercial invoice whose amount exceeds the amount
                        permitted by the credit, provided the bank does not honour or negotiate for an
                        amount in excess of the credit. I.e. an over-amount invoice is not automatically
                        discrepant — the bank caps settlement at the LC figure (subject to :39A:/:39B:
                        tolerance).
                        18(c) The description of the goods, services or performance in the commercial
                        invoice must CORRESPOND with that in the credit.
Applicability condition: Applies whenever a commercial invoice is a required document (almost always).
                        The single most important article for an invoice-only checker.
Relation to invoice/MT700: 18(a)(i) ↔ invoice issuer vs :59: (beneficiary); 18(a)(ii) ↔ invoice "bill
                        to" vs :50: (applicant); 18(a)(iii) ↔ invoice currency vs :32B: currency;
                        18(b) ↔ invoice total vs :32B: amount (+ :39A: tolerance); 18(c) ↔ invoice
                        goods description vs :45A:.
Source(s):              S4, S5 (ICC Academy, primary); S8 (18(b) secondary); S6.
Evidence confidence:    18(a),(b),(c) principles = Verified; signature sub-article locator = Unverified.
```

**Is a signature required on a commercial invoice under UCP 600?**
**Answer: No — unless the credit itself requires one.** UCP 600 Article 18 contains **no express requirement** that a commercial invoice be signed. The invoice need only *appear to be issued by the beneficiary* (18(a)(i)); appearance of issuance can be satisfied by letterhead/identity without a signature. In practice the signature requirement is triggered by the credit's **:46A: / :47A:** (e.g. "signed commercial invoice").
- **UNVERIFIED locator:** secondary sources disagree on which sub-article carries the "need not be signed" point (some cite 18(a)(iii), some 18(c), some just "Article 18"). No official-primary source confirmed a specific sub-article. The **principle** is cross-verified; the **locator** is not.

**Known divergence (over-amount invoices):** pre-UCP-600 wording allowed banks to *refuse* over-amount invoices; UCP 600 reversed this to "may accept" (capped payment). Some practitioners still treat an over-amount invoice as a discrepancy — this is not the UCP 600 position. See §5.6 dispute (b)1.

### Article 30 — Tolerance in Credit Amount, Quantity and Unit Prices

```text
Article:                30
title:                  Tolerance in Credit Amount, Quantity and Unit Prices
Paraphrased rule:       30(a) The words "about" or "approximately" used in connection with the credit
                        amount, the quantity, OR the unit price allow a tolerance NOT EXCEEDING 10% more
                        or 10% less.
                        30(b) Unless the credit states the quantity in stipulated packing units/individual
                        items and no tolerance is stated, a tolerance of ±5% is allowed on the quantity.
                        Does NOT apply to unit-price tolerances.
                        30(c) Even without "about"/"approximately", a tolerance NOT EXCEEDING 5% LESS
                        than the amount of the credit is allowed, provided the quantity, if stipulated,
                        has been shipped in full (and the unit-price / Art. 39(b) conditions are met).
Applicability condition: Engaged only when the LC uses "about"/"approximately" (30(a)), or when quantity
                        is measurable and no tolerance is stated (30(b)). :39A: (Percentage Credit Amount
                        Tolerance) operationalises amount tolerance at LC level; where :39A: is present it
                        typically states the agreed tolerance and overrides Art. 30 defaults.
Relation to invoice/MT700: 30(a) ↔ "about"/"approximately" in :32B:/:45A:; 30(b) ↔ quantity in :45A:;
                        30(c) ↔ invoice total vs :32B: amount; :39A: ↔ amount tolerance at LC level.
Source(s):              Studocu, AnyFlip, ovrseas, NNRV, practitioner posts (secondary) + search-result
                        quotation of 30(a).
Evidence confidence:    30(a) = Cross-verified; 30(b)/(c) headline = Cross-verified; 30(c) exact provisos
                        (unit-price-not-reduced, Art. 39(b) interaction) = Unverified.
```

**Known divergence:** where the credit states its own tolerance via :39A:, the mainstream view is that the stated tolerance controls and Art. 30 defaults do **not** additionally stack. Some practitioners mis-apply 30(a) on top of an explicit :39A:. See §5.6 dispute (b)4.

### Article 31 — Partial Drawings and Partial Shipments

```text
Article:                31
title:                  Partial Drawings or Shipments
Paraphrased rule:       31(a) Partial drawings and/or partial shipments are ALLOWED unless the credit
                        expressly forbids them.
                        31(b) Defines what constitutes a partial shipment by shipment type (transport-
                        specific; out of scope for an invoice-only checker).
Applicability condition: LIMITED relevance to an invoice-only checker. 31 governs drawing/shipment
                        splitting, not invoice face-content. An invoice checker only needs 31(a) indirectly
                        (a drawing may be for less than the full :32B: amount). 31(b) transport-specific
                        definitions are NOT relevant to invoice examination.
Relation to MT700:      :43P: (Partial Shipments) ↔ 31(a). :43T: (Transhipment) is Art. 32 territory.
Source(s):              S4 (ICC Academy).
Evidence confidence:    31(a) = Cross-verified; 31(b) detail = Unverified (out of scope, not pursued).
```

---

## 5.4 ISBP 821 paragraph list

**Foundational principle (S4, S5):** "ISBP does **not** amend UCP 600." ISBP 821 explains how UCP 600 practices are applied; the two must be read **together**, not in isolation (ISBP 821 Preliminary Consideration (i)). ISBP 821 = ICC Publication 821, **2023 revision**.

### Preliminary Considerations

```text
Paragraph:              Preliminary Consideration (iv)
Paraphrased content:    The applicant and beneficiary should carefully consider which documents are
                        required, by whom they are issued, their data content and time frames. Only
                        documents that are necessary (e.g. for customs) should be required; if feasible,
                        documentary requirements should be limited to an invoice and transport document.
Applicability:          General guidance for credit drafting.
Relation to UCP 600:    Supports Art. 14 examination scope.
Source(s):              S4.
Evidence confidence:    Verified.
Related UCP 600 article: Art. 14.
```

```text
Paragraph:              Preliminary Consideration (viii)
Paraphrased content:    Provided all stipulated documents are received by the issuing bank, the OMISSION
                        OR INCORRECT TYPING OF A CREDIT REFERENCE NUMBER in a document is NOT a
                        discrepancy — UNLESS the credit specifically requires the credit reference number
                        to be shown. (New addition in ISBP 821.)
Applicability:          This is the LC-number principle: an invoice is required to show the LC/credit
                        number ONLY when the credit (typically :46A: or :47A:) specifically demands it.
Relation to UCP 600:    Operationalises the Art. 14(d) "not conflict" standard for the credit-reference
                        number specifically; overrides older practice that treated a missing/typo LC number
                        as a discrepancy.
Source(s):              tradefinance.training (citing ICC TAB Briefing No. 8), Collyer Consulting,
                        SmartLC.ai, TFG (secondary, cross-verified).
Evidence confidence:    Cross-verified (principle). Verbatim paragraph text requires the paid ICC Pub. 821.
Related UCP 600 article: Art. 14(d).
```

```text
Paragraph:              Preliminary Consideration (ix)
Paraphrased content:    Concerns minor administrative / typographical errors that do not create confusion
                        about the identity of the credit or the documents; such errors should not
                        automatically be treated as discrepancies.
Applicability:          General examination guidance.
Relation to UCP 600:    Supports Art. 14(d) "conflict, not identical" standard.
Source(s):              TFG, Collyer Consulting (secondary).
Evidence confidence:    Cross-verified (exists; concerns minor errors). Precise scope UNVERIFIED — secondary
                        summaries disagree on exactly what (ix) covers.
Related UCP 600 article: Art. 14(d).
```

### Section C — Invoices (paragraphs C1–C15)

The ICC Academy (S5) confirms ISBP 821 dedicates **sections C1 to C15** to invoices. Individual paragraph numbers beyond C1 and C3 are not separately confirmed from official-primary public sources — see §5.6.

```text
Paragraph:              C1 — Title of the invoice
Paraphrased content:    C1(a) When an L/C requires a generic "invoice" without further description, a
                        document titled "invoice" (or "commercial invoice", "tax invoice", etc.) satisfies
                        the requirement.
                        C1(b) When an L/C requires a "commercial invoice", a document simply titled
                        "invoice" is sufficient. The invoice title must not CONFLICT with the title
                        required by the credit.
Applicability:          Invoice-title checks.
Relation to UCP 600:    C1 ↔ Art. 18(a) ("appear to be the invoice named in the credit").
Source(s):              S7 (cross-verified), S5 (range).
Evidence confidence:    Cross-verified.
Related UCP 600 article: Art. 18(a).
```

```text
Paragraph:              C2 — Issuer of the invoice  [UNVERIFIED paragraph number]
Paraphrased content:    Multiple secondary sources indicate an early Section C paragraph addresses that the
                        invoice must appear to be issued by the beneficiary. The exact paragraph number
                        "C2" could NOT be confirmed from an official source.
Applicability:          Invoice-issuer checks.
Relation to UCP 600:    Cross-references Art. 18(a)(i) (issued by the beneficiary).
Source(s):              Secondary only.
Evidence confidence:    Principle Cross-verified; paragraph number UNVERIFIED.
Related UCP 600 article: Art. 18(a)(i).
```

```text
Paragraph:              C3 — Description of goods
Paraphrased content:    The description of goods/services/performance shown on the invoice must CORRESPOND
                        with the description in the credit. There is NO requirement for a mirror image —
                        i.e. the invoice need not be a verbatim/word-for-word copy of the :45A: description.
                        Details may be stated differently provided they correspond and do not conflict.
                        ("correspond ≠ identical".)
Applicability:          Invoice goods-description checks.
Relation to UCP 600:    C3 ↔ Art. 18(c) ("correspond"); also Art. 14(d) ("not conflict").
Source(s):              S7, FG Capital, Studocu, practitioner posts (secondary, cross-verified).
Evidence confidence:    Cross-verified ("correspond, no mirror image").
Related UCP 600 article: Art. 18(c); Art. 14(d).
```

```text
Paragraph:              [Currency consistency]  [UNVERIFIED paragraph number]
Paraphrased content:    The invoice must be in the same currency as the credit.
Applicability:          Invoice currency checks.
Relation to UCP 600:    Operationalises Art. 18(a)(iii).
Source(s):              Secondary.
Evidence confidence:    Principle Cross-verified; Section-C paragraph number UNVERIFIED.
Related UCP 600 article: Art. 18(a)(iii).
```

```text
Paragraph:              [Value / amount consistency]  [UNVERIFIED paragraph number]
Paraphrased content:    The invoice's stated total must not conflict with its line items / arithmetic.
                        This is a CONSISTENCY check, NOT a re-calculation rule — banks examine on face and
                        check that the stated total does not conflict with the components; they do not
                        independently re-perform the maths as a compliance test.
Applicability:          Invoice amount/arithmetic checks.
Relation to UCP 600:    Supports Art. 14(d) and Art. 18(b).
Source(s):              Secondary.
Evidence confidence:    Principle Cross-verified; Section-C paragraph number UNVERIFIED.
Related UCP 600 article: Art. 14(d); Art. 18(b).
```

---

## 5.5 Mapping to the project's examination scenarios

For each scenario the project is expected to check, the applicable official rule(s) and the **precondition** that must hold before the rule applies. (Stated as scenarios — independent of any implementation.)

| Scenario | Applicable official rule(s) | Precondition |
|---|---|---|
| Invoice amount vs LC amount (tolerance) | UCP 600 Art. 30(a)/(b)/(c); Art. 18(b) (over-amount); MT700 :39A: / :32B: | Tolerance defaults apply unless :39A: states its own % (then :39A: controls). Over-amount is not automatically discrepant (18(b)). |
| Invoice currency vs LC currency | UCP 600 Art. 18(a)(iii); ISBP 821 Section C (currency, paragraph UNVERIFIED) | Always applies when an invoice is required. |
| Goods description / model / quantity correspondence | UCP 600 Art. 18(c); Art. 14(d); ISBP 821 C3 | "Correspond ≠ verbatim mirror"; must not conflict. 14(e) relaxes this for non-invoice documents. |
| Invoice issuer vs beneficiary | UCP 600 Art. 18(a)(i); ISBP 821 Section C (issuer, paragraph UNVERIFIED) | Always applies; invoice need not be signed unless :46A:/:47A: requires. |
| Invoice applicant vs LC applicant | UCP 600 Art. 18(a)(ii); Art. 14(j) | 14(j): addresses need not match — only the country must match. |
| Port of loading / port of discharge | UCP 600 Art. 14(d); MT700 :44E: / :44F: | 14(d) "not conflict" standard applies. NOTE ports are primarily a transport-document concern (Art. 20–23); applying a port check to the *invoice* is valid only under the general 14(d) consistency standard, not a dedicated invoice-port article. |
| LC reference number on the invoice | ISBP 821 Preliminary Consideration (viii) | **Only when the credit :46A: / :47A: specifically requires the credit reference number.** If not required, omission/mistyping is NOT a discrepancy. |
| MT700 mandatory-field absence | SWIFT MT700 message-structure rules (§5.2) — 11 mandatory fields | This is a MESSAGE-STRUCTURE check (valid MT700), NOT a UCP/ISBP document-examination rule. The two must not be conflated. |

---

## 5.6 Unverified or disputed items

### (a) Locators / facts NOT confirmed from a credible official-primary source

1. **`:39B:` Maximum Credit Amount in MT700** — S1 (SWIFT 2019) does **not** list `:39B:` in the MT700 format table; S2 (Nordea 2018) does. Cannot be resolved without the live SWIFT MyStandards entry. **Do not assume `:39B:` exists in the current MT700 standard.**
2. **`:71B:` in MT700** — `:71B:` is **not** an MT700 field (MT700 uses `:71D:`). `:71B:` belongs to MT 768/769 (guarantees). Any use of `:71B:` in an MT700/LC context is a mislabel.
3. **UCP 600 signature sub-article** — the principle "an invoice need not be signed unless the credit requires" is well-supported, but no official-primary source confirms which sub-article (18(a)(iii) vs 18(c) vs "Article 18 generally") carries it. Secondary sources contradict each other.
4. **UCP 600 30(c) exact provisos** — the "5% less" headline is cross-verified, but the exact conditions (unit-price-not-reduced, Art. 39(b) interaction, quantity-shipped-in-full) could not be confirmed verbatim from an official-primary public source.
5. **ISBP 821 Section C paragraph numbers beyond C1/C3** — the C1–C15 range is confirmed (S5), but individual numbers (C2 issuer, the currency paragraph, the arithmetic paragraph) are UNVERIFIED.
6. **ISBP 821 Preliminary Consideration (ix)** — confirmed to exist and to concern minor errors, but its precise scope is not fully corroborated across secondary summaries.

### (b) Points of known divergent banking-practice interpretation (no single "official conclusion" is picked)

1. **Invoice exceeding the LC amount (Art. 18(b)).** *Mainstream:* a bank **may accept** an over-amount invoice and cap honour/negotiation at the LC amount (subject to :39A:). *Divergence:* some practitioners still raise an over-amount invoice as a discrepancy. The two readings differ on whether over-amount is a *discrepancy* (refusable) or merely a *settlement cap*.
2. **"Correspond" strictness (Art. 18(c) / ISBP C3).** *Mainstream:* "correspond" ≠ verbatim mirror image; the invoice description must be consistent and not conflict, but may add detail or use different wording. *Divergence:* some banks historically applied near-mirror strictness. ISBP 821 C3 and TAB 12 push toward the conflict-only reading.
3. **Data "conflict" vs "inconsistency" (Art. 14(d)).** *Mainstream:* only genuine *conflict* is a discrepancy. *Divergence:* a minority still raise discrepancies for mere non-identical data that does not actually conflict — the practice UCP 600 deliberately tried to end.
4. **Tolerance stacking (Art. 30 vs :39A:).** *Mainstream:* where the credit states its own tolerance via :39A:, that stated tolerance controls and Art. 30 defaults do not additionally apply. *Divergence:* some argue 30(a) can be mis-applied on top of :39A:.
5. **Credit reference number omission (ISBP 821 Prelim (viii)).** *Mainstream (new in ISBP 821):* omission/mistyping of the LC number is NOT a discrepancy unless the credit requires it. *Divergence:* prior to ISBP 821 many banks treated a missing/typo LC number as a discrepancy; legacy practice persists.

### Copyright-boundary note

To confirm the following **verbatim**, the paid publications are required:
- **UCP 600 full article text** (ICC Pub. 600) — for the exact wording of Art. 14(a)–(j), 18(a)–(c), 30(a)–(c), 31. This document paraphrases; only 14(d) is quoted (≤1 sentence, from the public TAB 12 PDF).
- **ISBP 821 full paragraph text** (ICC Pub. 821, 2023) — for verbatim text of Preliminary Considerations (iv), (viii), (ix) and Section C paragraphs C1–C15. This document confirms locators/range and paraphrases content from public secondary summaries.
- **SWIFT MT current standard** (swift.com MyStandards / paid subscription) — to resolve the `:39B:` discrepancy and confirm the 2026 field set. S1 (2019 advance PDF) is the newest public edition used here.

Intentional limits: no block of >1 sentence of any ICC/SWIFT publication is reproduced; all short quotes are from public pages and ≤1 sentence; all paraphrases are in this document's own words; all locators are facts.

---

*End of reference. Compiled 2026-07-12 from public official/authoritative sources only. No project source code was read.*
