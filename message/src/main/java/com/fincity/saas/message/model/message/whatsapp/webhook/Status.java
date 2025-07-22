package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.webhook.type.MessageStatus;
import java.util.List;

public record Status(
        @JsonProperty("id") String id,
        @JsonProperty("conversation") Conversation conversation,
        @JsonProperty("pricing") Pricing pricing,
        @JsonProperty("recipient_id") String recipientId,
        @JsonProperty("status") MessageStatus status,
        @JsonProperty("timestamp") String timestamp,
        @JsonProperty("errors") List<Error> errors) {}
