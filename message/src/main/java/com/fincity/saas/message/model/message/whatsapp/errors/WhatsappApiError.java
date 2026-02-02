package com.fincity.saas.message.model.message.whatsapp.errors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class WhatsappApiError implements Serializable {

    @Serial
    private static final long serialVersionUID = 171537965594716615L;

    @JsonProperty("error")
    private Error error;
}
