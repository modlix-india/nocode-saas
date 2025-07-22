package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReactionMessage {
    @JsonProperty("message_id")
    private String messageId;

    @JsonProperty("emoji")
    private String emoji;

    public ReactionMessage(String messageId, String emoji) {
        this.messageId = messageId;
        this.emoji = emoji;
    }

    public ReactionMessage() {}

    public String getMessageId() {
        return messageId;
    }

    public ReactionMessage setMessageId(String messageId) {
        this.messageId = messageId;
        return this;
    }

    public String getEmoji() {
        return emoji;
    }

    public ReactionMessage setEmoji(String emoji) {
        this.emoji = emoji;
        return this;
    }
}
