package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TextMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = -5026698248389005713L;

    @JsonProperty("preview_url")
    private boolean previewUrl;

    @JsonProperty("body")
    private String body;
}
