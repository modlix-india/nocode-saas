package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Error(
        @JsonProperty("code") int code,
        @JsonProperty("title") String title,
        @JsonProperty("message") String message,
        @JsonProperty("error_data") ErrorData errorData,
        @JsonProperty("href") String href) {}
