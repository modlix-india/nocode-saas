package com.fincity.saas.message.model.message.whatsapp.media;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public final class Media {

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
