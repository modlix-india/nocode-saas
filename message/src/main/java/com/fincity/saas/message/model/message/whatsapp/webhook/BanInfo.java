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
public final class BanInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 8105587313070926677L;

    @JsonProperty("waba_ban_state")
    private String wabaBanState;

    @JsonProperty("waba_ban_date")
    private String wabaBanDate;
}
