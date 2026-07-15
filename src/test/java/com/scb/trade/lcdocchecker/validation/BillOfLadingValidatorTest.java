package com.scb.trade.lcdocchecker.validation;

import com.scb.trade.lcdocchecker.domain.BillOfLading;
import com.scb.trade.lcdocchecker.domain.DocumentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BillOfLadingValidatorTest {

    private final BillOfLadingValidator validator = new BillOfLadingValidator();

    @Test
    void handlesBillOfLadingType() {
        assertThat(validator.documentType()).isEqualTo(DocumentType.BILL_OF_LADING);
    }

    @Test
    void completeBol_isOk() {
        BillOfLading bol = new BillOfLading("BL-1", "Shipper", "Consignee", "Vessel",
                "Port of Singapore", "Port of Hamburg", "goods", "raw");
        ExtractionValidation v = validator.validate(bol);

        assertThat(v.status()).isEqualTo(ValidationStatus.OK);
        assertThat(v.findings()).isEmpty();
    }

    @Test
    void missingIdentityFields_warn() {
        // blNumber null, shipper blank, consignee null, portOfLoading null; vessel + portOfDischarge present
        BillOfLading bol = new BillOfLading(null, "  ", null, "Vessel", null, "Port of Hamburg", "goods", "raw");
        ExtractionValidation v = validator.validate(bol);

        assertThat(v.status()).isEqualTo(ValidationStatus.WARN);
        assertThat(v.findings()).extracting(ValidationFinding::ruleCode).containsExactlyInAnyOrder(
                "BOL_NUMBER_EMPTY", "BOL_SHIPPER_EMPTY", "BOL_CONSIGNEE_EMPTY", "BOL_PORT_OF_LOADING_EMPTY");
    }

    @Test
    void nullBol_isOk() {
        assertThat(validator.validate(null).status()).isEqualTo(ValidationStatus.OK);
    }
}
