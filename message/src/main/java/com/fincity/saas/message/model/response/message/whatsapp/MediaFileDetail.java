package com.fincity.saas.message.model.response.message.whatsapp;

import com.fincity.saas.message.model.message.whatsapp.media.FileType;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@FieldNameConstants
public class MediaFileDetail implements Serializable {

    @Serial
    private static final long serialVersionUID = 1683814048899955806L;

    private String fileName;
    private FileType mimeType;
    private String base64Content;

    public static MediaFileDetail of(String fileName, FileType mimeType, String base64Content) {
        return new MediaFileDetail().setFileName(fileName).setMimeType(mimeType).setBase64Content(base64Content);
    }
}
