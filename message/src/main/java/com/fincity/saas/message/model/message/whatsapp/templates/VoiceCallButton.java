package com.fincity.saas.message.model.message.whatsapp.templates;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fincity.saas.message.model.message.whatsapp.templates.type.ButtonType;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VoiceCallButton extends Button {

    @Serial
    private static final long serialVersionUID = -7217992295558920990L;

    protected VoiceCallButton() {
        super(ButtonType.VOICE_CALL);
    }

    public VoiceCallButton(String text) {
        super(ButtonType.VOICE_CALL, text);
    }
}
