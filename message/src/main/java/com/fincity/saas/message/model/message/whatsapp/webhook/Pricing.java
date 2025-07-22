package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Pricing(
        @JsonProperty("pricing_model") String pricingModel,
        @JsonProperty("category") String category,
        @JsonProperty("billable") boolean billable) {}
