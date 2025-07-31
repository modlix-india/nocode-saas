package com.fincity.saas.message.model.request.message;

import com.fincity.saas.message.model.message.whatsapp.messages.TemplateMessage;
import com.fincity.saas.message.model.request.BaseMessageRequest;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@FieldNameConstants
@EqualsAndHashCode(callSuper = true)
public class WhatsappTemplateRequest extends BaseMessageRequest {

    @Serial
    private static final long serialVersionUID = 1683814048899955813L;

    private String to;
    private TemplateMessage templateMessage;

    public boolean isValid() {
        return this.to != null && this.templateMessage != null 
                && this.templateMessage.getName() != null 
                && this.templateMessage.getLanguage() != null;
    }
}