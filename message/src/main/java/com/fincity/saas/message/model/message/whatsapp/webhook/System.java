package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

public record System(
        @JsonProperty("new_wa_id") String newWaId,
        @JsonProperty("body") String body,
        @JsonProperty("type") String type) {}
