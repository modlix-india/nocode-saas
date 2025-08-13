package com.fincity.saas.message.model.message.whatsapp.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommerceDataItem implements Serializable {

    @Serial
    private static final long serialVersionUID = -4114185347409853681L;

    @JsonProperty("id")
    private String id;

    @JsonProperty("is_catalog_visible")
    private Boolean isCatalogVisible;

    @JsonProperty("is_cart_enabled")
    private Boolean isCartEnabled;
}
