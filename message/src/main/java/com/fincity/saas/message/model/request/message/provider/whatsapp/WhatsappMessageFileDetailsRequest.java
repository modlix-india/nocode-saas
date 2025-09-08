package com.fincity.saas.message.model.request.message.provider.whatsapp;

import java.io.Serial;
import java.io.Serializable;

import com.fincity.saas.message.model.common.Identity;
import com.fincity.saas.message.oserver.files.model.FileDetail;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@FieldNameConstants
public class WhatsappMessageFileDetailsRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 3482193396454049129L;

    private Identity whatsappMessageId;
    private FileDetail fileDetail;
}
