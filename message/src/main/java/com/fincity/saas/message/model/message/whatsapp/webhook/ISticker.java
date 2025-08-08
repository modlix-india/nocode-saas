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
public final class ISticker implements Serializable {

    @Serial
    private static final long serialVersionUID = 6056031795773277980L;

    @JsonProperty("sha256")
    private String sha256;

    @JsonProperty("mime_type")
    private String mimeType;

    @JsonProperty("id")
    private String id;

    @JsonProperty("animated")
    private boolean animated;
}
