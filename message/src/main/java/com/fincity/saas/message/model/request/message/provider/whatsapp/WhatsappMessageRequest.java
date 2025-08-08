package com.fincity.saas.message.model.request.message.provider.whatsapp;

import com.fincity.saas.message.model.base.BaseMessageRequest;
import com.fincity.saas.message.model.common.Identity;
import com.fincity.saas.message.model.message.whatsapp.messages.Message;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@FieldNameConstants
@EqualsAndHashCode(callSuper = true)
public class WhatsappMessageRequest extends BaseMessageRequest {

    @Serial
    private static final long serialVersionUID = 1683814048899955812L;

    private Identity whatsappPhoneNumberId;
    private Message message;

    public boolean isValid() {
        return this.message != null && this.message.getTo() != null;
    }
}
