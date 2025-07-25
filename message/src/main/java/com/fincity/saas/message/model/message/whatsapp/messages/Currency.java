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
public class Currency implements Serializable {

    @Serial
    private static final long serialVersionUID = 3214929951212679113L;

    @JsonProperty("fallback_value")
    private String fallbackValue;

    @JsonProperty("code")
    private String code;

    @JsonProperty("amount_1000")
    private long amount1000;
}
