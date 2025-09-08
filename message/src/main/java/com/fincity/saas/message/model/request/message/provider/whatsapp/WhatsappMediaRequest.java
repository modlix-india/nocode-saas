package com.fincity.saas.message.model.request.message.provider.whatsapp;

import com.fincity.saas.message.model.base.BaseMessageRequest;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@FieldNameConstants
@EqualsAndHashCode(callSuper = true)
public class WhatsappMediaRequest extends BaseMessageRequest {

    @Serial
    private static final long serialVersionUID = 9075133442134460355L;

    private String mediaId;
    private String mediaUrl;
}
