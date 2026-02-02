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
public final class IEmail implements Serializable {

    @Serial
    private static final long serialVersionUID = 7699568442595348380L;

    @JsonProperty("type")
    private String type;

    @JsonProperty("email")
    private String email;
}
