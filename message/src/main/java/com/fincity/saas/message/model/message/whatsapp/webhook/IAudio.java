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
public final class IAudio implements Serializable {

    @Serial
    private static final long serialVersionUID = -828960371391692041L;

    @JsonProperty("mime_type")
    private String mimeType;

    @JsonProperty("sha256")
    private String sha256;

    @JsonProperty("id")
    private String id;

    @JsonProperty("voice")
    private boolean voice;
}
