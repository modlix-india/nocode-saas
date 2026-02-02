package com.fincity.saas.message.model.message.whatsapp.phone;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.business.WebhookConfig;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PhoneNumberWebhookConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 6003613824055319007L;

    @JsonProperty("webhook_configuration")
    private WebhookConfig webhookConfig;

    @JsonProperty("id")
    private String id;
}
