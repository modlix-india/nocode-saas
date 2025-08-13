package com.fincity.saas.message.model.message.whatsapp.media;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MediaFile implements Serializable {

    @Serial
    private static final long serialVersionUID = 8699072406009235176L;

    private String fileName;
    private byte[] content;
}
