package com.fincity.saas.entity.processor.analytics.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.security.model.User;
import com.fincity.saas.entity.processor.dto.product.Product;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class FilterablePageResponse<T> implements Page<T>, Serializable {

    @Serial
    private static final long serialVersionUID = 7823491065234718903L;

    private List<Client> clients;
    private List<Product> products;
    private List<User> assignedUsers;
    private List<User> clientManagers;

    @JsonIgnore
    private transient Page<T> page;

    public static <T> FilterablePageResponse<T> of(
            Page<T> page,
            List<Client> clients,
            List<Product> products,
            List<User> assignedUsers,
            List<User> clientManagers) {

        FilterablePageResponse<T> response = new FilterablePageResponse<>();
        response.page = page;
        response.clients = clients;
        response.products = products;
        response.assignedUsers = assignedUsers;
        response.clientManagers = clientManagers;
        return response;
    }

    // Page methods

    @Override
    public int getTotalPages() {
        return page.getTotalPages();
    }

    @Override
    public long getTotalElements() {
        return page.getTotalElements();
    }

    @Override
    public <U> FilterablePageResponse<U> map(Function<? super T, ? extends U> converter) {
        return FilterablePageResponse.of(page.map(converter), clients, products, assignedUsers, clientManagers);
    }

    // Slice methods

    @Override
    public int getNumber() {
        return page.getNumber();
    }

    @Override
    public int getSize() {
        return page.getSize();
    }

    @Override
    public int getNumberOfElements() {
        return page.getNumberOfElements();
    }

    @Override
    public List<T> getContent() {
        return page.getContent();
    }

    @Override
    public boolean hasContent() {
        return page.hasContent();
    }

    @Override
    public Sort getSort() {
        return page.getSort();
    }

    @Override
    public boolean isFirst() {
        return page.isFirst();
    }

    @Override
    public boolean isLast() {
        return page.isLast();
    }

    @Override
    public boolean hasNext() {
        return page.hasNext();
    }

    @Override
    public boolean hasPrevious() {
        return page.hasPrevious();
    }

    @Override
    public Pageable nextPageable() {
        return page.nextPageable();
    }

    @Override
    public Pageable previousPageable() {
        return page.previousPageable();
    }

    // Iterable / Streamable

    @Override
    public Iterator<T> iterator() {
        return page.iterator();
    }
}
