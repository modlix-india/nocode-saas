package com.fincity.saas.entity.processor.analytics.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.security.model.User;
import com.fincity.saas.entity.processor.dto.product.Product;
import com.fincity.saas.entity.processor.dto.product.ProductTemplate;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.ALWAYS)
public class FilterableListResponse<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 4918273650182734901L;

    private List<T> data;
    private List<Client> clients;
    private List<Product> products;
    private List<User> assignedUsers;
    private List<User> clientManagers;
    private List<ProductTemplate> productTemplates;
    private List<ProductTemplate> selectedProductTemplates;
    private List<StageHierarchy> stageHierarchies;

    public static <T> FilterableListResponse<T> of(
            List<T> data,
            List<Client> clients,
            List<Product> products,
            List<User> assignedUsers,
            List<User> clientManagers) {

        FilterableListResponse<T> response = new FilterableListResponse<>();
        response.data = data;
        response.clients = clients;
        response.products = products;
        response.assignedUsers = assignedUsers;
        response.clientManagers = clientManagers;
        return response;
    }

    public static <T> FilterableListResponse<T> of(
            List<T> data,
            List<Client> clients,
            List<Product> products,
            List<User> assignedUsers,
            List<User> clientManagers,
            List<ProductTemplate> productTemplates,
            List<ProductTemplate> selectedProductTemplates,
            List<StageHierarchy> stageHierarchies) {

        FilterableListResponse<T> response =
                of(data, clients, products, assignedUsers, clientManagers);
        response.productTemplates = productTemplates;
        response.selectedProductTemplates = selectedProductTemplates;
        response.stageHierarchies = stageHierarchies;
        return response;
    }
}
