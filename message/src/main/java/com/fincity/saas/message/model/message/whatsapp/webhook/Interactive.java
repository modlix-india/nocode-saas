package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Interactive(
        @JsonProperty("list_reply") ListReply listReply,
        @JsonProperty("type") String type,
        @JsonProperty("button_reply") ButtonReply buttonReply) {}
