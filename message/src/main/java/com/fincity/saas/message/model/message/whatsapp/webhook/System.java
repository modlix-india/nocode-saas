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
public final class System implements Serializable {

    @Serial
    private static final long serialVersionUID = 632145365990941622L;

    @JsonProperty("new_wa_id")
    private String newWaId;

    @JsonProperty("body")
    private String body;

    @JsonProperty("type")
    private String type;
}
