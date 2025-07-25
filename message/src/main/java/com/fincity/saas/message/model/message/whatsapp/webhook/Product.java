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
public final class Product implements Serializable {

    @Serial
    private static final long serialVersionUID = -473661913879147426L;

    @JsonProperty("quantity")
    private String quantity;

    @JsonProperty("product_retailer_id")
    private String productRetailerId;

    @JsonProperty("item_price")
    private String itemPrice;

    @JsonProperty("currency")
    private String currency;
}
