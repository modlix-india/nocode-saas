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
public final class Button implements Serializable {

    @Serial
    private static final long serialVersionUID = -3067801488939205343L;

    @JsonProperty("payload")
    private String payload;

    @JsonProperty("text")
    private String text;
}
