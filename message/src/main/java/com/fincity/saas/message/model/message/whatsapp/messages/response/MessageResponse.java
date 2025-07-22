package com.fincity.saas.message.model.message.whatsapp.messages.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class MessageResponse {

    @JsonProperty("messaging_product")
    private String messagingProduct;

    @JsonProperty("contacts")
    private List<Contact> contacts;

    @JsonProperty("messages")
    private List<Message> messages;
}
