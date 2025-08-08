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
public final class IContext implements Serializable {

    @Serial
    private static final long serialVersionUID = -498339957452993565L;

    @JsonProperty("from")
    private String from;

    @JsonProperty("referred_product")
    private IReferredProduct referredProduct;

    @JsonProperty("id")
    private String id;

    @JsonProperty("forwarded")
    private boolean forwarded;

    @JsonProperty("frequently_forwarded")
    private boolean frequentlyForwarded;
}
