package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.messages.type.ParameterType;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TextParameter extends Parameter implements Serializable {

    @Serial
    private static final long serialVersionUID = 1773246959454137331L;

    @JsonProperty("text")
    private final String text;

    public TextParameter(String text) {
        super(ParameterType.TEXT);
        this.text = text;
    }

    public String getText() {
        return text;
    }
}
