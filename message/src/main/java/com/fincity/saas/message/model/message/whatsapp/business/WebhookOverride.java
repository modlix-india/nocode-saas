package com.fincity.saas.message.model.message.whatsapp.business;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class WebhookOverride implements Serializable {

    @Serial
    private static final long serialVersionUID = 6126260666762371675L;

    @JsonProperty("override_callback_uri")
    private String overrideCallbackUri;

    @JsonProperty("verify_token")
    private String verifyToken;
}
