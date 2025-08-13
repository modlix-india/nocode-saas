package com.fincity.saas.message.model.message.whatsapp.messages.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Contact implements Serializable {

    @Serial
    private static final long serialVersionUID = 9221847349194789205L;

    @JsonProperty("input")
    private String input;

    @JsonProperty("wa_id")
    private String waId;
}
