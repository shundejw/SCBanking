package com.scb.trade.lcdocchecker.domain;

import java.math.BigDecimal;

/**
 * Domain representation of the fields extracted from the invoice PDF. Produced by the
 * extraction pipeline (PDFBox + OCR fallback + Spring AI) and consumed by the rule engine.
 *
 * <p>Fields beyond the original check set (invoiceNumber, invoiceDate, unitPrice, quantity) are
 * modelled for completeness per the case-study Input Spec but not yet consumed by a check
 * (YAGNI — add a check only when a rule requires it).
 */
public record InvoiceFields(
        String sellerName,         // beneficiary / issuer (letterhead)
        String applicantName,      // buyer (bill-to)
        String currency,           // ISO-4217
        BigDecimal totalAmount,    // invoice grand total
        String goodsDescription,   // free-text goods description
        String portOfLoading,      // optional
        String portOfDischarge,    // optional
        String lcReferenceNumber,  // optional — null when the invoice does not state it
        String sellerAddress,      // optional — used by the address-country check
        String applicantAddress,   // optional — used by the address-country check
        String invoiceNumber,      // optional — invoice identifier (Input Spec)
        String invoiceDate,        // optional — invoice issue date, raw as printed
        BigDecimal unitPrice,      // optional — per-unit price
        String quantity,           // optional — quantity as printed (e.g. "100 METRIC TONS")
        String rawText) implements ExtractedDocument {          // raw extracted text (for artifacts)

    @Override
    public DocumentType documentType() {
        return DocumentType.INVOICE;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Seed a builder with this record's current values (for incremental variations in tests). */
    public Builder toBuilder() {
        return new Builder()
                .sellerName(sellerName)
                .applicantName(applicantName)
                .currency(currency)
                .totalAmount(totalAmount)
                .goodsDescription(goodsDescription)
                .portOfLoading(portOfLoading)
                .portOfDischarge(portOfDischarge)
                .lcReferenceNumber(lcReferenceNumber)
                .sellerAddress(sellerAddress)
                .applicantAddress(applicantAddress)
                .invoiceNumber(invoiceNumber)
                .invoiceDate(invoiceDate)
                .unitPrice(unitPrice)
                .quantity(quantity)
                .rawText(rawText);
    }

    /** Mutable builder for test stubs and the LLM-output mapper. */
    public static final class Builder {
        private String sellerName;
        private String applicantName;
        private String currency;
        private BigDecimal totalAmount;
        private String goodsDescription;
        private String portOfLoading;
        private String portOfDischarge;
        private String lcReferenceNumber;
        private String sellerAddress;
        private String applicantAddress;
        private String invoiceNumber;
        private String invoiceDate;
        private BigDecimal unitPrice;
        private String quantity;
        private String rawText;

        public Builder sellerName(String v) { this.sellerName = v; return this; }
        public Builder applicantName(String v) { this.applicantName = v; return this; }
        public Builder currency(String v) { this.currency = v; return this; }
        public Builder totalAmount(BigDecimal v) { this.totalAmount = v; return this; }
        public Builder totalAmount(String v) { this.totalAmount = v == null ? null : new BigDecimal(v); return this; }
        public Builder goodsDescription(String v) { this.goodsDescription = v; return this; }
        public Builder portOfLoading(String v) { this.portOfLoading = v; return this; }
        public Builder portOfDischarge(String v) { this.portOfDischarge = v; return this; }
        public Builder lcReferenceNumber(String v) { this.lcReferenceNumber = v; return this; }
        public Builder sellerAddress(String v) { this.sellerAddress = v; return this; }
        public Builder applicantAddress(String v) { this.applicantAddress = v; return this; }
        public Builder invoiceNumber(String v) { this.invoiceNumber = v; return this; }
        public Builder invoiceDate(String v) { this.invoiceDate = v; return this; }
        public Builder unitPrice(BigDecimal v) { this.unitPrice = v; return this; }
        public Builder unitPrice(String v) { this.unitPrice = v == null ? null : new BigDecimal(v); return this; }
        public Builder quantity(String v) { this.quantity = v; return this; }
        public Builder rawText(String v) { this.rawText = v; return this; }

        public InvoiceFields build() {
            return new InvoiceFields(sellerName, applicantName, currency, totalAmount,
                    goodsDescription, portOfLoading, portOfDischarge, lcReferenceNumber,
                    sellerAddress, applicantAddress, invoiceNumber, invoiceDate, unitPrice,
                    quantity, rawText);
        }
    }
}
