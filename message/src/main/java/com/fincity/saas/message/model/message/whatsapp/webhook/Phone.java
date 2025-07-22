package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Phone(
        @JsonProperty("phone") String phone, @JsonProperty("wa_id") String waId, @JsonProperty("type") String type) {}
