package com.fincity.saas.message.model.message.whatsapp.templates;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fincity.saas.message.model.message.whatsapp.templates.type.ButtonType;


import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VoiceCallButton extends Button {

    
    protected VoiceCallButton() {
        super(ButtonType.VOICE_CALL);
    }

    
    public VoiceCallButton(String text) {
        super(ButtonType.VOICE_CALL, text);
    }
}
