package com.fincity.security.controller;

import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.util.ConditionUtil;
import com.fincity.security.dto.ClientActivity;
import com.fincity.security.service.ClientActivityService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/client-activities")
public class ClientActivityController {

    private final ClientActivityService service;

    public ClientActivityController(ClientActivityService service) {
        this.service = service;
    }

    @PostMapping
    public Mono<ResponseEntity<ClientActivity>> create(@RequestBody ClientActivity entity) {
        return this.service.create(entity).map(ResponseEntity::ok);
    }

    @GetMapping("/{clientId}")
    public Mono<ResponseEntity<Page<ClientActivity>>> readPageFilter(
            @PathVariable ULong clientId,
            Pageable pageable,
            ServerHttpRequest request) {

        pageable = pageable == null
                ? PageRequest.of(0, 10, Sort.Direction.DESC, "createdAt")
                : pageable;

        return this.service
                .readPageFilter(clientId, pageable,
                        ConditionUtil.parameterMapToMap(request.getQueryParams()))
                .flatMap(page -> this.service.fillCreatedByUser(page.getContent()).thenReturn(page))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/{clientId}/query")
    public Mono<ResponseEntity<Page<ClientActivity>>> readPageFilterQuery(
            @PathVariable ULong clientId,
            @RequestBody Query query) {

        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), query.getSort());

        return this.service
                .readPageFilter(clientId, pageable, query.getCondition())
                .flatMap(page -> this.service.fillCreatedByUser(page.getContent()).thenReturn(page))
                .map(ResponseEntity::ok);
    }
}
