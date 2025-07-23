package com.fincity.saas.message.model.message.whatsapp.messages.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Message implements Serializable {

    @Serial
    private static final long serialVersionUID = 5559427114893544785L;

    @JsonProperty("id")
    private String id;

    @JsonProperty("message_status")
    private String messageStatus;
}
