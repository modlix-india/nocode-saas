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
public final class SubscribedApp implements Serializable {

    @Serial
    private static final long serialVersionUID = 2920017312668265820L;

    @JsonProperty("whatsapp_business_api_data")
    private BusinessApiData businessApiData;

    @JsonProperty("override_callback_uri")
    private String overrideCallBackUrl;
}
