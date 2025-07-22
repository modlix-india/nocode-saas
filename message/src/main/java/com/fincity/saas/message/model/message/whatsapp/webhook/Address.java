package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Address(
        @JsonProperty("zip") String zip,
        @JsonProperty("country") String country,
        @JsonProperty("country_code") String countryCode,
        @JsonProperty("city") String city,
        @JsonProperty("street") String street,
        @JsonProperty("state") String state,
        @JsonProperty("type") String type) {}
