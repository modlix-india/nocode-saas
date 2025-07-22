package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.messages.type.ParameterType;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ButtonTextParameter extends Parameter {
    @JsonProperty("text")
    private String text;

    public ButtonTextParameter() {
        super(ParameterType.TEXT);
    }

    public ButtonTextParameter(String text) {
        super(ParameterType.TEXT);
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public ButtonTextParameter setText(String text) {
        this.text = text;
        return this;
    }
}
