package com.scb.trade.lcdocchecker.validation;

import com.scb.trade.lcdocchecker.domain.BillOfLading;
import com.scb.trade.lcdocchecker.domain.DocumentType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Data-quality guardrail for bills of lading: required identity/logistics fields present.
 * Parallel to {@link InvoiceFieldValidator}; registered by {@link #documentType()} so the
 * orchestrator validates BoL documents and persists the {@code extraction_validation} artifact
 * without any core change.
 */
@Component
public class BillOfLadingValidator implements FieldValidator<BillOfLading> {

    @Override
    public DocumentType documentType() {
        return DocumentType.BILL_OF_LADING;
    }

    @Override
    public ExtractionValidation validate(BillOfLading bol) {
        if (bol == null) {
            return ExtractionValidation.ok();
        }
        List<ValidationFinding> findings = new ArrayList<>();
        require(findings, "BOL_NUMBER_EMPTY", "bill of lading number", bol.blNumber(), "blNumber");
        require(findings, "BOL_SHIPPER_EMPTY", "shipper", bol.shipper(), "shipper");
        require(findings, "BOL_CONSIGNEE_EMPTY", "consignee", bol.consignee(), "consignee");
        require(findings, "BOL_PORT_OF_LOADING_EMPTY", "port of loading", bol.portOfLoading(), "portOfLoading");
        require(findings, "BOL_PORT_OF_DISCHARGE_EMPTY", "port of discharge", bol.portOfDischarge(), "portOfDischarge");
        return ExtractionValidation.of(findings);
    }

    private static void require(List<ValidationFinding> findings, String code, String label,
                                String value, String fieldPath) {
        if (value == null || value.isBlank()) {
            findings.add(ValidationFinding.of(Severity.WARNING, code,
                    "Bill of lading " + label + " is absent.", fieldPath));
        }
    }
}
