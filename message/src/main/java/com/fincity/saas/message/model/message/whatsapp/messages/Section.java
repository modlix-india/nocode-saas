package com.fincity.saas.message.model.message.whatsapp.messages;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Section {

    @JsonProperty("title")
    public String title;

    @JsonProperty("product_items")
    public List<Product> products;

    @JsonProperty("rows")
    public List<Row> rows;

    public String getTitle() {
        return title;
    }

    public Section setTitle(String title) {
        this.title = title;
        return this;
    }

    public List<Product> getProducts() {
        return products;
    }

    public Section setProducts(List<Product> products) {
        this.products = products;
        return this;
    }

    public Section addProductItem(Product product) {
        if (this.products == null) this.products = new ArrayList<>();

        this.products.add(product);
        return this;
    }

    public List<Row> getRows() {
        return rows;
    }

    public Section setRows(List<Row> rows) {
        this.rows = rows;
        return this;
    }

    public Section addRow(Row row) {
        if (this.rows == null) this.rows = new ArrayList<>();

        this.rows.add(row);
        return this;
    }
}
