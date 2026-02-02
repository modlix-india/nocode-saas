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
public class QuickReplyButton extends Button {

    @Serial
    private static final long serialVersionUID = 8774845526397876407L;

    protected QuickReplyButton() {
        super(ButtonType.QUICK_REPLY);
    }

    public QuickReplyButton(String text) {
        super(ButtonType.QUICK_REPLY, text);
    }
}
