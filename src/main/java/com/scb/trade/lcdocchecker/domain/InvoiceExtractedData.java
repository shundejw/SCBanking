package com.scb.trade.lcdocchecker.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Target type for the Spring AI structured-output extraction
 * ({@code BeanOutputConverter<InvoiceExtractedData>}). Field aliases tolerate the
 * snake_case / camelCase variability of LLM output and map cleanly into
 * {@link InvoiceFields}.
 */
public record InvoiceExtractedData(
        @JsonProperty("seller_name") @JsonAlias({"sellerName", "issuerName", "issuer_name", "beneficiaryName"}) String sellerName,
        @JsonProperty("applicant_name") @JsonAlias({"applicantName", "buyerName", "buyer_name"}) String applicantName,
        @JsonProperty("currency") String currency,
        @JsonProperty("total_amount") @JsonAlias({"totalAmount", "invoiceAmount", "invoice_amount", "amount"}) BigDecimal totalAmount,
        @JsonProperty("goods_description") @JsonAlias({"goodsDescription", "description"}) String goodsDescription,
        @JsonProperty("port_of_loading") @JsonAlias({"portOfLoading", "loadingLocation", "loading_location"}) String portOfLoading,
        @JsonProperty("port_of_discharge") @JsonAlias({"portOfDischarge", "destinationPort", "destination_port", "destinationLocation", "destination_location"}) String portOfDischarge,
        @JsonProperty("lc_reference_number") @JsonAlias({"lcReferenceNumber", "lcNumber", "lc_number", "importReference", "import_reference"}) String lcReferenceNumber,
        @JsonProperty("seller_address") @JsonAlias({"sellerAddress", "beneficiaryAddress", "issuer_address"}) String sellerAddress,
        @JsonProperty("applicant_address") @JsonAlias({"applicantAddress", "buyerAddress", "buyer_address"}) String applicantAddress,
        @JsonProperty("invoice_number") @JsonAlias({"invoiceNumber", "invoice_no", "invoiceNo"}) String invoiceNumber,
        @JsonProperty("invoice_date") @JsonAlias({"invoiceDate", "issue_date", "date"}) String invoiceDate,
        @JsonProperty("unit_price") @JsonAlias({"unitPrice", "price_per_unit", "price"}) BigDecimal unitPrice,
        @JsonProperty("quantity") @JsonAlias({"qty", "quantity_supplied"}) String quantity) {

    public InvoiceFields toFields(String rawText) {
        return InvoiceFields.builder()
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
                .rawText(rawText)
                .build();
    }
}
