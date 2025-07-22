package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Location(
        @JsonProperty("address") String address,
        @JsonProperty("latitude") double latitude,
        @JsonProperty("name") String name,
        @JsonProperty("longitude") double longitude,
        @JsonProperty("url") String url) {}
