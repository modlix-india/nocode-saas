package com.fincity.saas.message.model.request.message.provider.whatsapp.business;

import com.fincity.saas.message.model.base.BaseMessageRequest;
import com.fincity.saas.message.model.common.Identity;
import com.fincity.saas.message.model.message.whatsapp.templates.MessageTemplate;
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
    private static final long serialVersionUID = 2058302969096289863L;

    private Identity whatsappTemplateId;
    private MessageTemplate messageTemplate;
}
