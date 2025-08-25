package com.fincity.saas.message.model.request.message.provider.whatsapp.graph;

import com.fincity.saas.message.model.base.BaseMessageRequest;
import com.fincity.saas.message.model.message.whatsapp.media.FileType;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@FieldNameConstants
@EqualsAndHashCode(callSuper = true)
public class UploadSessionRequest extends BaseMessageRequest {

    @Serial
    private static final long serialVersionUID = 3527324702446961836L;

    private String fileName;
    private long fileLength;
    private FileType fileType;
}
