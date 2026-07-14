package com.scb.trade.lcdocchecker.checks;

import com.scb.trade.lcdocchecker.domain.CheckResult;
import com.scb.trade.lcdocchecker.domain.DocumentType;
import com.scb.trade.lcdocchecker.domain.ExtractedDocument;
import com.scb.trade.lcdocchecker.domain.LcTerms;

import java.util.Set;

/**
 * A single, independently-implemented document check. Checks are gathered by Spring and
 * executed by {@code CheckEngineService} in deterministic {@code @Order}.
 *
 * @param <D> the extracted document type this check operates on
 */
public interface DocumentCheck<D extends ExtractedDocument> {

    /** Stable identifier used by the rule allowlist ({@code lcchecker.rules}). */
    String checkId();

    /**
     * Document types this check applies to. The engine only invokes {@link #execute} for
     * documents whose {@link ExtractedDocument#documentType()} is in this set, so a check
     * never receives a document type it was not declared to handle.
     */
    Set<DocumentType> appliesTo();

    /**
     * Evaluate this check against the LC terms and the extracted document.
     *
     * @return {@link CheckStatus#PASS}, {@link CheckStatus#FAIL} (with a {@code Discrepancy}),
     *         {@link CheckStatus#NOT_APPLICABLE}, or {@link CheckStatus#UNABLE}.
     */
    CheckResult execute(LcTerms lc, D document);
}
