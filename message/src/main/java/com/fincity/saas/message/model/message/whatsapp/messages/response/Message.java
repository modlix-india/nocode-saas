package com.fincity.saas.message.model.message.whatsapp.messages.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Message {

    @JsonProperty("id")
    private String id;

    @JsonProperty("message_status")
    private String messageStatus;
}
