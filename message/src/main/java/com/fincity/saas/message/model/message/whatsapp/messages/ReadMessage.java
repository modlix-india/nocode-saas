package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ReadMessage {

    @JsonProperty("messaging_product")
    private final String messagingProduct = "whatsapp";

    @JsonProperty("status")
    private String status = "read";

    @JsonProperty("message_id")
    private String messageId;

    public ReadMessage(String messageId) {
        super();
        this.messageId = messageId;
    }

    public String getStatus() {
        return status;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getMessagingProduct() {
        return messagingProduct;
    }
}
