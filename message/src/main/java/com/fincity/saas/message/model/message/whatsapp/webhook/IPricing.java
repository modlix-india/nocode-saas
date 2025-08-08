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
public final class IPricing implements Serializable {

    @Serial
    private static final long serialVersionUID = -1002871746227092062L;

    @JsonProperty("pricing_model")
    private String pricingModel;

    @JsonProperty("category")
    private String category;

    @JsonProperty("billable")
    private boolean billable;
}
