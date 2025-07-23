package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Reaction implements Serializable {

    @Serial
    private static final long serialVersionUID = -2233018195385697111L;

    @JsonProperty("emoji")
    private String emoji;

    @JsonProperty("message_id")
    private String messageId;
}
