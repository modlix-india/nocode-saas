package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Button(@JsonProperty("payload") String payload, @JsonProperty("text") String text) {}
