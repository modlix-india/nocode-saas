package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Product {

    @JsonProperty("product_retailer_id")
    public String productRetailerId;

    public String getProductRetailerId() {
        return productRetailerId;
    }

    public Product setProductRetailerId(String productRetailerId) {
        this.productRetailerId = productRetailerId;
        return this;
    }
}
