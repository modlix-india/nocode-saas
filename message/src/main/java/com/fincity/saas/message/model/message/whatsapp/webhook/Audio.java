package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Audio(
        @JsonProperty("mime_type") String mimeType,
        @JsonProperty("sha256") String sha256,
        @JsonProperty("id") String id,
        @JsonProperty("voice") boolean voice) {}
