package com.scb.trade.lcdocchecker.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies {@link InvoiceExtractedData#toFields(String)} maps every field — including the
 * modelled-but-not-yet-checked invoice metadata (invoiceNumber/invoiceDate/unitPrice/quantity)
 * added in Gap 5 — into {@link InvoiceFields}.
 */
class InvoiceExtractedDataTest {

    @Test
    void toFieldsMapsAllExtractedFields() {
        InvoiceExtractedData data = new InvoiceExtractedData(
                "XYZ EXPORT", "ABC IMPORT", "USD", new BigDecimal("57500.00"),
                "100 MT SUGAR", "Singapore", "Hamburg", "LC-1", "Addr A", "Addr B",
                "INV-001", "2026-07-14", new BigDecimal("575.00"), "100 METRIC TONS");

        InvoiceFields f = data.toFields("raw text");

        assertEquals("XYZ EXPORT", f.sellerName());
        assertEquals("ABC IMPORT", f.applicantName());
        assertEquals("USD", f.currency());
        assertEquals(new BigDecimal("57500.00"), f.totalAmount());
        assertEquals("100 MT SUGAR", f.goodsDescription());
        assertEquals("Singapore", f.portOfLoading());
        assertEquals("Hamburg", f.portOfDischarge());
        assertEquals("LC-1", f.lcReferenceNumber());
        assertEquals("Addr A", f.sellerAddress());
        assertEquals("Addr B", f.applicantAddress());
        assertEquals("INV-001", f.invoiceNumber());
        assertEquals("2026-07-14", f.invoiceDate());
        assertEquals(new BigDecimal("575.00"), f.unitPrice());
        assertEquals("100 METRIC TONS", f.quantity());
        assertEquals("raw text", f.rawText());
        assertEquals(DocumentType.INVOICE, f.documentType());
    }

    @Test
    void toFieldsMapsNullsForAbsentMetadata() {
        InvoiceExtractedData data = new InvoiceExtractedData(
                "S", "B", "USD", new BigDecimal("100.00"), "g", null, null, null, null, null,
                null, null, null, null);
        InvoiceFields f = data.toFields("raw");

        assertNull(f.invoiceNumber());
        assertNull(f.invoiceDate());
        assertNull(f.unitPrice());
        assertNull(f.quantity());
        assertNull(f.portOfLoading());
    }
}
