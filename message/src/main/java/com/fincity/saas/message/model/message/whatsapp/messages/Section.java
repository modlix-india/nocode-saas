package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Section implements Serializable {

    @Serial
    private static final long serialVersionUID = -815010317089461622L;

    @JsonProperty("title")
    public String title;

    @JsonProperty("product_items")
    public List<Product> products;

    @JsonProperty("rows")
    public List<Row> rows;

    public Section addProductItem(Product product) {
        if (this.products == null) this.products = new ArrayList<>();

        this.products.add(product);
        return this;
    }

    public Section addRow(Row row) {
        if (this.rows == null) this.rows = new ArrayList<>();

        this.rows.add(row);
        return this;
    }
}
