# SCB LC Invoice Checker

Automated **Letter of Credit (LC) vs Invoice** document checker for Trade Finance, validating commercial invoices against SWIFT MT700 LC terms under **UCP 600 / ISBP 821** rules and producing a structured JSON discrepancy report.

The service ingests an LC (SWIFT MT700 plain text) and an Invoice (PDF), parses the LC programmatically, extracts invoice data (PDF text layer → OCR fallback → LLM structured extraction), runs a modular deterministic rule engine, persists every intermediate artifact for auditability, and exposes a clean REST API.

## Tech Stack

| Layer | Choice |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 4.1.0 |
| LLM | Spring AI 2.0.0 (`ChatClient` + structured output via `BeanOutputConverter`, OpenAI-compatible endpoint) |
| PDF | Apache PDFBox 3.0.7 (text layer) |
| OCR fallback | PaddleOCR HTTP sidecar (`RestClient`) |
| JSON | Jackson 3 (`tools.jackson`; retains `com.fasterxml.jackson.annotation` for DTO annotations) |
| Precision | `java.math.BigDecimal` for all monetary / tolerance arithmetic |
| Build | Maven |

## Architecture

```
POST /checks  →  UploadGuard  →  LcParserService (MT700 state machine)
                              →  DocumentExtractorService (PDFBox → OCR → Spring AI)
                              →  CheckEngineService (deterministic @Order rule engine)
                              →  ReportAssemblerService ({compliant, discrepancies})
                              →  ArtifactStoreService (per-runId JSON artifacts)
```

Every pipeline stage persists an intermediate artifact (`lc_parsed`, `invoice_extracted`, `pdf_text`, `check_results`, `final_report`) under a run ID, retrievable via the inspection API.

## REST API

| Method | Path | Description |
|---|---|---|
| `POST` | `/checks` | multipart: `lc` (raw MT700 text) + `invoice` (PDF) → `CheckReport` |
| `GET` | `/checks/{runId}` | final compliance report |
| `GET` | `/checks/{runId}/artifacts/{stage}` | intermediate artifact JSON |

**Response contract** (matches the case-study spec):
```json
{
  "compliant": false,
  "discrepancies": [
    {
      "field": "totalAmount",
      "lc_value": "Max Allowed: 60375.00",
      "presented_value": "63000.00",
      "rule_reference": "UCP 600 Art. 18(b)",
      "description": "The invoice amount exceeds the maximum tolerance drawing limits allowed by LC terms."
    }
  ]
}
```

Errors return a standard `{ "error": "<CODE>", "message": "<detail>" }` body with HTTP 400/404/422/500 — no stack traces leaked.

## Rule Engine

Deterministic, `@Order`-driven, allowlist-filtered (`lcchecker.rules.enabled`). Every discrepancy cites a UCP 600 / ISBP 821 reference sourced from a single `rulebook.RuleReference` enum (no ad-hoc citations).

| Check | Article | Behavior |
|---|---|---|
| Amount | UCP 600 Art. 18(b) | BigDecimal ceiling; `:39B:` NOT EXCEEDING → `tolerancePlus=0`; neutral banking wording |
| Currency | UCP 600 Art. 18(a)(iii) | exact match; `NOT_APPLICABLE` if not extracted |
| Issuer name | UCP 600 Art. 18(a)(i) | word-level Jaccard (≥0.85), designator-stripped, accent-folded — no substring match |
| Applicant name | UCP 600 Art. 18(a)(ii) | same conservative matcher |
| Address country | UCP 600 Art. 14(j) | country-only; `UNABLE` if address not stably extractable |
| Goods description | UCP 600 Art. 18(c) | token correspondence (equal/subset/Jaccard); `NOT_APPLICABLE` if missing |
| Port of loading | UCP 600 Art. 14(d) | separate field; no substring-overlap as conclusive |
| Port of discharge | UCP 600 Art. 14(d) | separate field |
| LC reference | ISBP 821 Prelim. (viii) | conditional — only when LC `:46A:` mandates it |
| Signature | — | `UNABLE` (routed to manual review; never fabricates PASS/FAIL) |
| Quantity | UCP 600 Art. 30(b) | tolerance band; `NOT_APPLICABLE` when not stably extractable (registered; off by default — see config) |

Conservative by design: missing/uncertain evidence → `NOT_APPLICABLE` or `UNABLE`, never a silent pass or a fabricated fail.

## Build & Run

```bash
# Build + run the full test suite (55 tests)
mvn clean test

# Run the service
mvn spring-boot:run
# or build a runnable jar
mvn package -DskipTests
java -jar target/lc-checker-1.0.0-SNAPSHOT.jar
```

### Configuration

LLM access is environment-injected (never hardcoded):
```bash
export SPRING_AI_API_KEY=...
```
All tunable knobs (OCR sidecar URL/timeout, rule allowlist, artifact root, LLM model/temperature) live in `src/main/resources/application.yml`.

### OCR sidecar (optional)

Scanned PDFs with an insufficient text layer fall back to a PaddleOCR HTTP sidecar. See `docs/Dockerfile` for a reference container. The sidecar URL and timeout are configurable under `lcchecker.ocr.*`.

## Project Structure

```
src/main/java/com/scb/trade/lcdocchecker/
├── api/            # CheckController, ComplianceOrchestratorService
├── checks/         # DocumentCheck interface + each rule + CheckEngineService + NameNormalizer
├── config/         # @ConfigurationProperties (ocr, rules, upload, artifact) + SpringAiConfig
├── domain/         # LcTerms, InvoiceFields, InvoiceExtractedData, Discrepancy, CheckResult, CheckReport, ...
├── exception/      # GlobalExceptionHandler + typed exceptions
├── extractor/      # PdfTextExtractor, PaddleOcrSidecarClient, InvoiceExtractionService (Spring AI)
├── guard/          # UploadGuardService (magic bytes / size / pages / LC length)
├── parser/         # LcParserService (MT700 Block 4 state machine)
├── report/         # ReportAssemblerService
├── rulebook/       # RuleReference (single source of UCP 600 / ISBP 821 citations)
└── store/          # ArtifactStoreService, CheckRunStore
src/main/resources/
├── application.yml
└── prompts/invoice-extraction-v1.st
src/test/java/...   # parser, extractor, guard, rule, manifest, controller integration tests
```

## Notes

- Design and audit documentation (`docs/`) and test fixtures are maintained separately and pushed independently.
- The discrepancy JSON keys are snake_case (`lc_value`, `presented_value`, `rule_reference`) per the case-study output spec; DTOs accept snake_case + camelCase + legacy aliases via `@JsonAlias`.
