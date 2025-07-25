package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReactionMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 6370666198562711086L;

    @JsonProperty("message_id")
    private String messageId;

    @JsonProperty("emoji")
    private String emoji;
}
