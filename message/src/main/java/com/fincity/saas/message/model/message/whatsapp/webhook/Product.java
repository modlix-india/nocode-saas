package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Product(
        @JsonProperty("quantity") String quantity,
        @JsonProperty("product_retailer_id") String productRetailerId,
        @JsonProperty("item_price") String itemPrice,
        @JsonProperty("currency") String currency) {}
