package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Reaction(@JsonProperty("emoji") String emoji, @JsonProperty("message_id") String messageId) {}
