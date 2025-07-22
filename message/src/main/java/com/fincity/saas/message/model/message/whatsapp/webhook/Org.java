package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Org(
        @JsonProperty("company") String company,
        @JsonProperty("department") String department,
        @JsonProperty("title") String title) {}
