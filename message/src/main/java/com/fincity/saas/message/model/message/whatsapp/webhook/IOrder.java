package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class IOrder implements Serializable {

    @Serial
    private static final long serialVersionUID = -663142045624654331L;

    @JsonProperty("catalog_id")
    private String catalogId;

    @JsonProperty("product_items")
    private List<IProduct> productItems;

    @JsonProperty("text")
    private String text;
}
