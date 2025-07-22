package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.messages.type.MessageType;
import java.util.List;

public record Message(
        @JsonProperty("reaction") Reaction reaction,
        @JsonProperty("image") Image image,
        @JsonProperty("sticker") Sticker sticker,
        @JsonProperty("location") Location location,
        @JsonProperty("contacts") List<Contact> contacts,
        @JsonProperty("button") Button button,
        @JsonProperty("context") Context context,
        @JsonProperty("from") String from,
        @JsonProperty("id") String id,
        @JsonProperty("text") Text text,
        @JsonProperty("errors") List<Error> errors,
        @JsonProperty("type") MessageType type,
        @JsonProperty("interactive") Interactive interactive,
        @JsonProperty("timestamp") String timestamp,
        @JsonProperty("referral") Referral referral,
        @JsonProperty("order") Order order,
        @JsonProperty("system") System system,
        @JsonProperty("video") Video video,
        @JsonProperty("audio") Audio audio,
        @JsonProperty("document") Document document) {}
