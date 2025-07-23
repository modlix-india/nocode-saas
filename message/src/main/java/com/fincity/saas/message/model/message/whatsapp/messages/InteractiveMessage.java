package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.messages.type.InteractiveMessageType;
import com.whatsapp.api.domain.messages.builder.IInteractiveMessageBuilder.IInteractiveAction;
import com.whatsapp.api.domain.messages.builder.IInteractiveMessageBuilder.IInteractiveType;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InteractiveMessage implements IInteractiveAction, IInteractiveType, Serializable {

    @Serial
    private static final long serialVersionUID = -1823615047017247166L;

    @JsonProperty("action")
    public Action action;

    @JsonProperty("type")
    public InteractiveMessageType type;

    @JsonProperty("header")
    public Header header;

    @JsonProperty("body")
    public Body body;

    @JsonProperty("footer")
    public Footer footer;

    public static IInteractiveAction build() {
        return new InteractiveMessage();
    }

    @Override
    public IInteractiveType setAction(com.whatsapp.api.domain.messages.Action action) {
        return null;
    }

    @Override
    public com.whatsapp.api.domain.messages.InteractiveMessage setType(
            com.whatsapp.api.domain.messages.type.InteractiveMessageType type) {
        return null;
    }
}
