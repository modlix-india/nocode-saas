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
public class QuickReplyButton extends Button {
    
    protected QuickReplyButton() {
        super(ButtonType.QUICK_REPLY);
    }

    
    public QuickReplyButton(String text) {
        super(ButtonType.QUICK_REPLY, text);
    }
}
