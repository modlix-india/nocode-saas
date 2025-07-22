package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ButtonReply(@JsonProperty("id") String id, @JsonProperty("title") String title) {}
