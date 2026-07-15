package com.scb.trade.lcdocchecker.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BillOfLadingExtractedDataTest {

    @Test
    void toBillOfLadingMapsAllFieldsAndRawText() {
        BillOfLadingExtractedData data = new BillOfLadingExtractedData(
                "BL-9", "ShipperX", "ConsigneeY", "MSC Vega", "SGSIN", "DEHAM", "1000 bags of sugar");

        BillOfLading bol = data.toBillOfLading("RAW TEXT");

        assertThat(bol.blNumber()).isEqualTo("BL-9");
        assertThat(bol.shipper()).isEqualTo("ShipperX");
        assertThat(bol.consignee()).isEqualTo("ConsigneeY");
        assertThat(bol.vessel()).isEqualTo("MSC Vega");
        assertThat(bol.portOfLoading()).isEqualTo("SGSIN");
        assertThat(bol.portOfDischarge()).isEqualTo("DEHAM");
        assertThat(bol.goodsDescription()).isEqualTo("1000 bags of sugar");
        assertThat(bol.rawText()).isEqualTo("RAW TEXT");
        assertThat(bol.documentType()).isEqualTo(DocumentType.BILL_OF_LADING);
    }
}
