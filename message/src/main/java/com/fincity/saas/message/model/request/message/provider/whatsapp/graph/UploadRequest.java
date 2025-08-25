package com.fincity.saas.message.model.request.message.provider.whatsapp.graph;

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
public class UploadRequest extends BaseMessageRequest {

    @Serial
    private static final long serialVersionUID = 2408405228459380129L;

    private String uploadSessionId;
    private Long fileOffset; // optional, defaults to 0 when not provided
    private byte[] fileContent; // base64-encoded in JSON
}
