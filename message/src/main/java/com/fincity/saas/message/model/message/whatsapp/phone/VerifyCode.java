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
public final class VerifyCode implements Serializable {

    @Serial
    private static final long serialVersionUID = 8306561523946397441L;

    @JsonProperty("code")
    private String code;
}
