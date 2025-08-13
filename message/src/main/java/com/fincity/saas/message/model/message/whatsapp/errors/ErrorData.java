package com.fincity.saas.message.model.message.whatsapp.errors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ErrorData implements Serializable {

    @Serial
    private static final long serialVersionUID = 1697459750021694855L;

    @JsonProperty("messaging_product")
    private String messagingProduct;

    @JsonProperty("details")
    private String details;

    @JsonProperty("blame_field_specs")
    private Object blameFieldSpecs;
}
