package com.scb.trade.lcdocchecker.checks;

import com.scb.trade.lcdocchecker.domain.CheckResult;
import com.scb.trade.lcdocchecker.domain.DocumentType;
import com.scb.trade.lcdocchecker.domain.ExtractedDocument;
import com.scb.trade.lcdocchecker.domain.InvoiceFields;
import com.scb.trade.lcdocchecker.domain.LcTerms;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the document-type dispatch contract of {@link CheckEngineService}: a check is
 * only invoked for documents whose {@link ExtractedDocument#documentType()} is in the
 * check's {@link DocumentCheck#appliesTo()} set.
 */
class DocumentTypeDispatchTest {

    @Test
    void checkWhoseAppliesToExcludesInvoiceIsNotInvokedForInvoiceDocument() {
        AtomicBoolean executed = new AtomicBoolean();
        DocumentCheck<InvoiceFields> neverForInvoice = new DocumentCheck<>() {
            @Override public String checkId() { return "never_for_invoice"; }
            @Override public Set<DocumentType> appliesTo() { return EnumSet.noneOf(DocumentType.class); }
            @Override public CheckResult execute(LcTerms lc, InvoiceFields doc) {
                executed.set(true);
                return CheckResult.pass(checkId());
            }
        };
        CheckEngineService engine = new CheckEngineService(List.of(neverForInvoice));
        InvoiceFields invoice = InvoiceFields.builder()
                .sellerName("S").applicantName("A").currency("USD")
                .totalAmount(new BigDecimal("100.00")).build();

        // lc is irrelevant: the check is filtered out before execute, so it never touches lc.
        List<CheckResult> results = engine.run(null, invoice);

        assertFalse(executed.get(), "check not applicable to INVOICE must be filtered out before execute");
        assertTrue(results.isEmpty(), "no checks should have run");
    }
}
