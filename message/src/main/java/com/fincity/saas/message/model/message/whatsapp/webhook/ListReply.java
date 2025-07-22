package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ListReply(
        @JsonProperty("description") String description,
        @JsonProperty("id") String id,
        @JsonProperty("title") String title) {}
