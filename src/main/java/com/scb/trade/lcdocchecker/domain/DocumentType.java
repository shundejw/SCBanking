package com.scb.trade.lcdocchecker.domain;

/**
 * The kind of trade document being checked. Registered document types are enumerated here;
 * adding a new type requires only adding a constant — a compile-safe registration, not a
 * core-logic change (see {@link ExtractedDocument} for the document-model contract).
 */
public enum DocumentType {

    /** Commercial invoice — the initial, fully-implemented document type. */
    INVOICE
}
