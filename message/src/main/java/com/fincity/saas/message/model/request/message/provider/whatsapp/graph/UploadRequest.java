package com.fincity.saas.message.model.request.message.provider.whatsapp.graph;

import java.io.Serial;

import com.fincity.saas.message.model.base.BaseMessageRequest;
import com.fincity.saas.message.model.message.whatsapp.graph.UploadSessionId;

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

    private UploadSessionId uploadSessionId;
    private Long fileOffset;

    public static UploadRequest of(String connectionName, String uploadSessionId, Long fileOffset) {
        return (UploadRequest) new UploadRequest()
                .setUploadSessionId(UploadSessionId.of(uploadSessionId))
                .setFileOffset(fileOffset)
                .setConnectionName(connectionName);
    }

    public static UploadRequest of(String connectionName, String uploadSessionId, String fileOffset) {
        return of(connectionName, uploadSessionId, Long.parseLong(fileOffset));
    }

    public static UploadRequest of(String connectionName, String uploadSessionId) {
        return of(connectionName, uploadSessionId, 0L);
    }
}
