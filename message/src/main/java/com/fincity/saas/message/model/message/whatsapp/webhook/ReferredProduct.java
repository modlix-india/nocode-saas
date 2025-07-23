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
public final class ReferredProduct implements Serializable {

    @Serial
    private static final long serialVersionUID = -6588866042733745381L;

    @JsonProperty("catalog_id")
    private String catalogId;

    @JsonProperty("product_retailer_id")
    private String productRetailerId;
}
