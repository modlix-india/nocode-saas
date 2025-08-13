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
public final class IPhone implements Serializable {

    @Serial
    private static final long serialVersionUID = 3366197284647035591L;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("wa_id")
    private String waId;

    @JsonProperty("type")
    private String type;
}
