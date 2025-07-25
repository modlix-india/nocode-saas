package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Image implements Serializable {

    @Serial
    private static final long serialVersionUID = -5746450698357746482L;

    @JsonProperty("sha256")
    private String sha256;

    @JsonProperty("mime_type")
    private String mimeType;

    @JsonProperty("caption")
    private String caption;

    @JsonProperty("id")
    private String id;
}
