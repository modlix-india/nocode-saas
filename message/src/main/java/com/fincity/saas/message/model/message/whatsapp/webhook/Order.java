package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record Order(
        @JsonProperty("catalog_id") String catalogId,
        @JsonProperty("product_items") List<Product> productItems,
        @JsonProperty("text") String text) {}
