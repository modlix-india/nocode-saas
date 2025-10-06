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
public final class WebhookConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1049358810558822098L;

    @JsonProperty("phone_number")
    private String phoneNumber;

    @JsonProperty("whatsapp_business_account")
    private String whatsappBusinessAccount;

    @JsonProperty("application")
    private String application;
}
