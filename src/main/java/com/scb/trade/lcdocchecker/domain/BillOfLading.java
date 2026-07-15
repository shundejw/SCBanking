package com.scb.trade.lcdocchecker.domain;

/**
 * Domain representation of a bill of lading extracted from a PDF. Produced by
 * {@code BillOfLadingExtractor} and consumed by the data-quality validator. Transport-document
 * compliance checks (UCP 20-23) are out of scope for the invoice checker, so this type currently
 * supports extraction + validation only — adding BoL-specific checks later requires only a new
 * {@code DocumentType.BILL_OF_LADING}-scoped check bean.
 */
public record BillOfLading(
        String blNumber,
        String shipper,
        String consignee,
        String vessel,
        String portOfLoading,
        String portOfDischarge,
        String goodsDescription,
        String rawText) implements ExtractedDocument {

    @Override
    public DocumentType documentType() {
        return DocumentType.BILL_OF_LADING;
    }
}
