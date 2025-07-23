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
public final class Metadata implements Serializable {

    @Serial
    private static final long serialVersionUID = -2539533120386924308L;

    @JsonProperty("phone_number_id")
    private String phoneNumberId;

    @JsonProperty("display_phone_number")
    private String displayPhoneNumber;
}
