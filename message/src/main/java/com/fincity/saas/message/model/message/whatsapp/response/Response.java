package com.fincity.saas.message.model.message.whatsapp.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Response implements Serializable {

    @Serial
    private static final long serialVersionUID = -225800032263826931L;

    @JsonProperty("success")
    private boolean success;
}
