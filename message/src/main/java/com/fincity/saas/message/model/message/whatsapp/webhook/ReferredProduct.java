package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReferredProduct(
        @JsonProperty("catalog_id") String catalogId, @JsonProperty("product_retailer_id") String productRetailerId) {}
