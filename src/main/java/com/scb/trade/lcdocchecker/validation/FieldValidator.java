package com.scb.trade.lcdocchecker.validation;

import com.scb.trade.lcdocchecker.domain.DocumentType;
import com.scb.trade.lcdocchecker.domain.ExtractedDocument;

/**
 * Per-document-type data-quality validator strategy. Each document type registers one
 * {@code @Component} bean; the orchestrator looks it up by {@link #documentType()} to validate
 * the extracted document and persist the result as the {@code extraction_validation} artifact.
 *
 * <p>Adding validation for a new document type = adding one implementing bean (no core change),
 * mirroring the {@code DocumentExtractor} extensibility model.
 *
 * @param <D> the extracted document type validated
 */
public interface FieldValidator<D extends ExtractedDocument> {

    /** The document type this validator handles; drives registry lookup. */
    DocumentType documentType();

    /** Run deterministic field-contract / self-consistency checks over the extracted document. */
    ExtractionValidation validate(D document);
}
