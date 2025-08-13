package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.messages.type.ButtonType;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Button implements Serializable {

    @Serial
    private static final long serialVersionUID = 2453842713606526727L;

    @JsonProperty("type")
    public ButtonType type;

    @JsonProperty("reply")
    public Reply reply;
}
