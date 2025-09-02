package com.fincity.saas.message.model.message.whatsapp.phone;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(value = JsonInclude.Include.NON_NULL)
public final class TwoStepCode implements Serializable {

    @Serial
    private static final long serialVersionUID = -9071814074545721498L;

    @JsonProperty("pin")
    private String pin;
}
