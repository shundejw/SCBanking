package com.scb.trade.lcdocchecker.extractor;

import com.scb.trade.lcdocchecker.domain.DocumentType;
import com.scb.trade.lcdocchecker.domain.ExtractedDocument;

/**
 * Strategy for mapping raw document text → a typed {@link ExtractedDocument}. Each document
 * type has its own implementation with its own prompt, schema and post-processing.
 *
 * <p>Registered beans are collected by {@link DocumentExtractorService} and dispatched by
 * {@link #documentType()}. To support a new document type, add a new implementing
 * {@code @Service} — no changes to this interface or the dispatcher.
 *
 * @param <D> the extracted document type produced
 */
public interface DocumentExtractor<D extends ExtractedDocument> {

    /** The document type this extractor produces; drives dispatch. */
    DocumentType documentType();

    /** Extract the structured document from its raw text. */
    D extract(String text);
}
