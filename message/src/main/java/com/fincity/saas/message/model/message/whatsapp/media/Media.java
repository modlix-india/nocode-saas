package com.fincity.saas.message.model.message.whatsapp.media;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Media implements Serializable {

    @Serial
    private static final long serialVersionUID = 8274534464059037341L;

    @JsonProperty("sha256")
    private String sha256;

    @JsonProperty("mime_type")
    private FileType mimeType;

    @JsonProperty("messaging_product")
    private String messagingProduct;

    @JsonProperty("id")
    private String id;

    @JsonProperty("url")
    private String url;

    @JsonProperty("file_size")
    private long fileSize;
}
