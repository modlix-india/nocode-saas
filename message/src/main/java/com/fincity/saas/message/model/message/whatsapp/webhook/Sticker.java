package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Sticker(
        @JsonProperty("sha256") String sha256,
        @JsonProperty("mime_type") String mimeType,
        @JsonProperty("id") String id,
        @JsonProperty("animated") boolean animated) {}
