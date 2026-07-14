package com.scb.trade.lcdocchecker.domain;

/**
 * Common contract for all extracted trade documents (invoice, packing list, bill of lading,
 * beneficiary certificate, ...). Each concrete document implements this and self-reports its
 * {@link DocumentType}, so the check engine and orchestrator can dispatch by type without
 * down-casting.
 *
 * <p>Deliberately <b>not</b> {@code sealed}: adding a new document type must not require editing
 * this root interface — only registering a {@link DocumentType} constant and adding a new
 * implementing class.
 */
public interface ExtractedDocument {

    /** The document type this instance represents; drives check and extractor dispatch. */
    DocumentType documentType();

    /** Raw text extracted from the source document (for artifacts and audit). */
    String rawText();
}
