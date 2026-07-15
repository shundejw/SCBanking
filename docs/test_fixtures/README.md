# LC Invoice Checker Fixtures

All people, companies, banks, account details, and trade transactions in this directory are
fictional. The files are synthetic test data, not trade documents.

This directory holds the rebuilt, deterministic LC x commercial-invoice fixture matrix. Every
business value below is derived from the **actual** MT700 and PDF file contents (audited
2026-07-12), not from filenames or legacy examples. The machine-readable source of truth is
[`expected-results.json`](expected-results.json); the per-invoice files in [`expected/`](expected/)
are the response-body subset of the same data.

## Baseline audit conclusions

| Candidate LC | Verdict | Used as |
|---|---|---|
| `lc/SWIFT_MT700_Sample_Compliant.mt700` (LC202607120001) | Valid + internally consistent | **Main baseline** |
| `lc/LC-DEMO-2026-0002-import-reference-fixed.mt700` (LCDEMO2026-0002) | Valid (repaired :32B:) | **Import-reference baseline** (scenario K) |
| `lc/MT700_Valid.mt700` (LC20260001) | Valid; goods intentionally vague | **Parser-compat baseline** (extra message shape) |
| `lc/MT700_Invalid_Missing32B.mt700` (LCERR001) | Intentionally invalid (no :32B:) | **Invalid-input baseline** (scenario L) |

Repaired during this work (minimal, structure-only):
- `LC-DEMO-2026-0002-import-reference-fixed.mt700` was already the corrected form of the legacy
  `LC-DEMO-2026-0002-import-reference.mt700` (which had a truncated `:32B:USD57500,`). The fixed
  file uses a well-formed `:32B:USD57500,00`. No further edit was needed.
- A parser multi-line bug was fixed so the tag-on-own-line party format (`:50:\nNAME`) parses
  correctly (see `tmp/AUDIT-REPORT.md`).


## Main baseline facts (SWIFT_MT700_Sample_Compliant.mt700)

- LC number: **LC202607120001**; currency/amount: **USD 57,500.00**; tolerance `:39A: 5/5`
  → permitted drawing range **USD 54,625.00 … USD 60,375.00**.
- Applicant (buyer, :50:): **ABC IMPORTERS PTE LTD**. Beneficiary (seller, :59:):
  **XYZ EXPORT CO., LTD.**
- Port of loading (:44E:): **PORT OF SINGAPORE**. Port of discharge (:44F:): **PORT OF HAMBURG**.
- Goods (:45A:): **100 METRIC TONS OF REFINED SUGAR, INCOTERMS 2020 CIF HAMBURG**.
- The LC-number requirement in this LC sits in :47A:, which the V1 parser does not map into the
  rule context; therefore the LC-reference rule does **not** activate for the main baseline. This
  keeps the LC-number discrepancy unique to scenario K.

## Test matrix

`ocr_required` = the PDF has no usable text layer, so OCR fallback MUST run. For the digital cases
the native text layer is extracted (OCR is not triggered, and `PdfTextExtractorTest` proves OCR is
**not** called). For the scanned case, `PdfTextExtractorTest` proves OCR **is** called.

| ID | LC | Invoice PDF | PDF type | OCR | Target difference | Expected | Discrepancy field (rule) |
|---|---|---|---|---|---|---|---|
| compliant-digital | main | invoice-compliant-digital.pdf | digital | no | — | compliant | — |
| compliant-scanned | main | invoice-compliant-scanned.pdf | scanned | **yes** | — | compliant | — |
| compliant-hybrid | main | invoice-compliant-hybrid.pdf | **hybrid (p1 digital + p2 scanned)** | **yes (p2 only)** | per-page TEXT/OCR routing on a mixed doc | compliant | — |
| amount-exceeds | main | invoice-amount-exceeds.pdf | digital | no | total USD 63,000.00 > 60,375.00 ceiling | not compliant | totalAmount (UCP 600 Art. 18(b)) |
| goods-model-mismatch | main | invoice-goods-model-mismatch.pdf | digital | no | REFINED SUGAR → BROWN SUGAR | not compliant | goodsDescription (UCP 600 Art. 18(c)) |
| goods-quantity-mismatch | main | invoice-goods-quantity-mismatch.pdf | digital | no | 100 MT → 80 MT (total kept at 57,500) | not compliant | goodsDescription (UCP 600 Art. 18(c)) |
| seller-mismatch | main | invoice-seller-mismatch.pdf | digital | no | issuer → ACME SUGAR TRADING LTD | not compliant | issuerName (UCP 600 Art. 18(a)(i)) |
| buyer-mismatch | main | invoice-buyer-mismatch.pdf | digital | no | applicant → NORDIC TRADING ASIA PTE LTD | not compliant | applicantName (UCP 600 Art. 18(a)(ii)) |
| loading-location-mismatch | main | invoice-loading-location-mismatch.pdf | digital | no | Port of Singapore → Port of Shanghai | not compliant | portOfLoading (UCP 600 Art. 14(d)) |
| destination-mismatch | main | invoice-destination-mismatch.pdf | digital | no | Port of Hamburg → Port of Rotterdam | not compliant | portOfDischarge (UCP 600 Art. 14(d)) |
| currency-mismatch | main | invoice-currency-mismatch.pdf | digital | no | USD → EUR (numeric total unchanged) | not compliant | currency (UCP 600 Art. 18(a)(iii)) |
| import-reference-missing | import-ref | invoice-import-reference-missing.pdf | digital | no | LC number omitted although :46A: requires it | not compliant | lcReferenceNumber (ISBP 821 Prelim. (viii) / LC :46A) |
| import-reference-compliant | import-ref | invoice-import-reference-compliant.pdf | digital | no | — (companion to the above) | compliant | — |
| compliant-mt700-valid | parser-compat | invoice-compliant-mt700-valid.pdf | digital | no | — (second valid MT700 shape) | compliant | — |
| invalid-mt700-missing-32b | invalid | invoice-compliant-digital.pdf | digital | no | MT700 missing mandatory :32B: | **error 422** | — (input validation, not a discrepancy) |
| unreadable-pdf | main | invoice-unreadable.pdf | corrupt | no | PDF cannot be loaded/rendered | **error 422** | — (extraction error) |
| ocr-insufficient | main | invoice-compliant-scanned.pdf | scanned | yes | OCR returns insufficient content | **error 422** | — (extraction error; unit-test layer) |

Each negative scenario introduces **only** the target difference. Where a difference could cascade
(e.g. quantity change forcing an amount change), the fixture is designed so the cascade does not
occur (see e.g. `goods-quantity-mismatch`, where the unit price is adjusted so the total stays
within tolerance and only the goods-description discrepancy fires).

### Exact expected values

The discrepancy `field`, `lc_value`, `presented_value`, `rule_reference`, and `description` for
every case — plus the actual LC and invoice values they are derived from — are encoded in
[`expected-results.json`](expected-results.json) and asserted by
`ExpectedResultsManifestTest` (real parser + real rules) and `InvoiceCheckControllerIntegrationTest`
(real HTTP flow). The values are not duplicated here to avoid drift; the manifest is the source of
truth.

## Rule-reference provenance (verified against in-repo references)

All citations below are documented in [`docs/reference/`](../docs/reference/) (engineering
paraphrases of the ICC/SWIFT standards — see pending items for what could not be verified verbatim):

- `UCP 600 Art. 18(a)(i)` issuer/beneficiary · `18(a)(ii)` applicant · `18(a)(iii)` currency ·
  `18(b)` amount ceiling (with `:39A:`/Art. 30 tolerance) · `18(c)` goods description.
- `UCP 600 Art. 14(d)` data must not conflict (used for stated port comparison).
- `ISBP 821 Preliminary Consideration (viii)` — an absent/mistyped LC reference is not normally a
  refusal reason **except** where the LC (or an importing-country rule) expressly requires it. The
  import-reference baseline's :46A: states that requirement, so the discrepancy is sourced to
  `ISBP 821 Preliminary Consideration (viii) / LC :46A`.

> Note: the legacy README cited `ISBP 821 C1` for the missing-LC-number case. `C1` is the invoice
> **title** paragraph, not the LC-number rule, so that citation was incorrect and has been removed.

## Output and error contract

- Successful review → HTTP 200 `{ "compliant": <bool>, "discrepancies": [ ... ] }`.
- Discrepancy keys are snake_case per the case-study output spec: `field`, `lc_value`,
  `presented_value`, `rule_reference`, `description`. A missing presented value is JSON `null`
  (e.g. the omitted LC number in scenario K), never the string `"null"`.
- Discrepancy order is deterministic (rule-engine order: amount, issuer, applicant, goods,
  port-of-loading, port-of-discharge, lc-reference).
- LC number is checked **only** when the LC expressly requires it (its :46A: contains
  "CREDIT NUMBER"). It is not a discrepancy merely because the invoice omits a non-required number.
- An invalid MT700 (e.g. missing mandatory :32B:) and an unreadable/insufficient PDF are
  **input/processing errors** (HTTP 422, body `{ "error": "UNPROCESSABLE_ENTITY", "message": ... }`),
  not ordinary document discrepancies. The service never fabricates an LC amount or reports a fake
  discrepancy for these cases.

## Reproduction

```bash
# 1) Regenerate every invoice PDF deterministically (ReportLab + Poppler):
/Users/scott/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 \
    tools/generate_invoice_samples.py

# 2) Sync the per-invoice expected/*.json from the manifest:
/Users/scott/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/bin/python3 \
    tools/sync_expected_files.py

# 3) Automated verification (deterministic; no live LLM/OCR needed):
mvn test
```

### Required environment / dependencies
- JDK 21, Maven 3.9, Spring Boot 4.1.0, Spring AI 2.0.0, Apache PDFBox 3.0.7 (see `pom.xml`).
- Python 3 with ReportLab; Poppler `pdftoppm` (for the scanned variant).
- Live end-to-end only: a PaddleOCR sidecar on `localhost:8866` and `SPRING_AI_API_KEY` for the DeepSeek
  endpoint. The deterministic `mvn test` mocks both.

## Known limitations and pending rule confirmation

- **Rule text is paraphrased, not verbatim.** `docs/reference/*` are engineering paraphrases of
  UCP 600 / ISBP 821 / SWIFT MT700. Verbatim ICC/SWIFT text is not in the repo (copyright). The
  citations used are the ones documented in those references; production rule decisions must be
  confirmed against the official publications.
- **Port comparison rule basis.** There is no UCP article specifically mandating that an
  invoice-stated port match the LC; the engine uses the general `UCP 600 Art. 14(d)` (data must not
  conflict). The port rules compare only when **both** LC and invoice state a port; an omitted
  optional port is never a discrepancy.
- **Goods comparison on commodity LCs.** The main baseline goods (refined sugar) have no
  "Model"/"QUANTITY: ... UNITS" structure, so goods discrepancies fall under the general text
  correspondency check (`UCP 600 Art. 18(c)`). The structured `Model`/quantity path
  (`UCP 600 Art. 18(c) / ISBP 821 C3`) is exercised by the pumps-based import-reference baseline.
- **Regex fallback** (`ComplianceOrchestrator.mapToCommercialInvoiceFallback`) is coupled to legacy
  pumps patterns and uses a non-deterministic invoice date; it is out of scope for this rebuild and
  is never exercised by the deterministic test path (the LLM is mocked, not made to throw).
- **Scanned end-to-end** depends on the PaddleOCR sidecar; in the automated suite the OCR sidecar
  is mocked for determinism (OCR invocation is verified by `PdfTextExtractorTest`).
