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
public final class WebhookConfigOverride implements Serializable {

    @Serial
    private static final long serialVersionUID = 3618301645704915381L;

    @JsonProperty("webhook_configuration")
    private WebhookOverride webhookConfiguration;
}
