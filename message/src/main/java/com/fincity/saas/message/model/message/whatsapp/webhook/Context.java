package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Context(
        @JsonProperty("from") String from,
        @JsonProperty("referred_product") ReferredProduct referredProduct,
        @JsonProperty("id") String id,
        @JsonProperty("forwarded") boolean forwarded,
        @JsonProperty("frequently_forwarded") boolean frequentlyForwarded) {}
