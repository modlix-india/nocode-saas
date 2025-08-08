package com.fincity.saas.message.model.message.whatsapp.templates;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fincity.saas.message.model.message.whatsapp.templates.type.ButtonType;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = PhoneNumberButton.class, name = "PHONE_NUMBER"),
    @JsonSubTypes.Type(value = UrlButton.class, name = "URL"),
    @JsonSubTypes.Type(value = QuickReplyButton.class, name = "QUICK_REPLY"),
    @JsonSubTypes.Type(value = VoiceCallButton.class, name = "VOICE_CALL")
})
public class Button implements Serializable {

    @Serial
    private static final long serialVersionUID = -5334174774675056911L;

    private ButtonType type;
    private String text;

    protected Button(ButtonType type, String text) {
        this.type = type;
        this.text = text;
    }

    public Button(ButtonType buttonType) {
        this.type = buttonType;
    }
}
