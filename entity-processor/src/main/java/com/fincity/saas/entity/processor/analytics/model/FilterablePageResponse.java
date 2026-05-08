package com.fincity.saas.entity.processor.analytics.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import org.springframework.data.domain.Page;

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
public class FilterablePageResponse<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 7823491065234718903L;

    private List<T> content;
    private int totalPages;
    private long totalElements;
    private int number;
    private int size;
    private int numberOfElements;
    private boolean first;
    private boolean last;
    private boolean empty;

    private List<Client> clients;
    private List<Product> products;
    private List<User> assignedUsers;
    private List<User> clientManagers;
    private List<ProductTemplate> productTemplates;
    private List<ProductTemplate> selectedProductTemplates;
    private List<StageHierarchy> stageHierarchies;

    private static <T> FilterablePageResponse<T> fromPage(Page<T> page) {
        FilterablePageResponse<T> response = new FilterablePageResponse<>();
        response.content = page.getContent();
        response.totalPages = page.getTotalPages();
        response.totalElements = page.getTotalElements();
        response.number = page.getNumber();
        response.size = page.getSize();
        response.numberOfElements = page.getNumberOfElements();
        response.first = page.isFirst();
        response.last = page.isLast();
        response.empty = page.isEmpty();
        return response;
    }

    public static <T> FilterablePageResponse<T> of(
            Page<T> page,
            List<Client> clients,
            List<Product> products,
            List<User> assignedUsers,
            List<User> clientManagers) {

        FilterablePageResponse<T> response = fromPage(page);
        response.clients = clients;
        response.products = products;
        response.assignedUsers = assignedUsers;
        response.clientManagers = clientManagers;
        return response;
    }

    public static <T> FilterablePageResponse<T> of(
            Page<T> page,
            List<Client> clients,
            List<Product> products,
            List<User> assignedUsers,
            List<User> clientManagers,
            List<ProductTemplate> productTemplates,
            List<ProductTemplate> selectedProductTemplates,
            List<StageHierarchy> stageHierarchies) {

        FilterablePageResponse<T> response =
                of(page, clients, products, assignedUsers, clientManagers);
        response.productTemplates = productTemplates;
        response.selectedProductTemplates = selectedProductTemplates;
        response.stageHierarchies = stageHierarchies;
        return response;
    }
}
