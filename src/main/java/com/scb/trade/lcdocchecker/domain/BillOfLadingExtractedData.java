package com.scb.trade.lcdocchecker.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Target type for the Spring AI structured-output extraction of a bill of lading
 * ({@code BeanOutputConverter<BillOfLadingExtractedData>}). Field aliases tolerate the
 * snake_case / camelCase variability of LLM output and map cleanly into {@link BillOfLading}.
 */
public record BillOfLadingExtractedData(
        @JsonProperty("bl_number") @JsonAlias({"blNumber", "bill_of_lading_number", "bl_no", "bol_number"}) String blNumber,
        @JsonProperty("shipper") @JsonAlias({"consignor"}) String shipper,
        @JsonProperty("consignee") String consignee,
        @JsonProperty("vessel") @JsonAlias({"vesselName", "vessel_name", "ship_name"}) String vessel,
        @JsonProperty("port_of_loading") @JsonAlias({"portOfLoading", "loading_port", "port_loading"}) String portOfLoading,
        @JsonProperty("port_of_discharge") @JsonAlias({"portOfDischarge", "discharge_port", "destination_port"}) String portOfDischarge,
        @JsonProperty("goods_description") @JsonAlias({"goodsDescription", "description_of_goods", "description"}) String goodsDescription) {

    public BillOfLading toBillOfLading(String rawText) {
        return new BillOfLading(blNumber, shipper, consignee, vessel, portOfLoading, portOfDischarge,
                goodsDescription, rawText);
    }
}
