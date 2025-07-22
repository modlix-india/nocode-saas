package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Email(@JsonProperty("type") String type, @JsonProperty("email") String email) {}
