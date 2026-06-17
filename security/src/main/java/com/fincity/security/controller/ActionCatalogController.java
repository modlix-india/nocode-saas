package com.fincity.security.controller;

import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.billing.ActionCatalogDAO;
import com.fincity.security.dto.billing.ActionCatalog;
import com.fincity.security.jooq.tables.records.SecurityActionCatalogRecord;
import com.fincity.security.service.billing.ActionCatalogService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/action-catalog")
public class ActionCatalogController extends AbstractJOOQUpdatableDataController<SecurityActionCatalogRecord,
        ULong, ActionCatalog, ActionCatalogDAO, ActionCatalogService> {

    public ActionCatalogController(ActionCatalogService service) {
        this.service = service;
    }

    @GetMapping("/key/{actionKey}")
    public Mono<ResponseEntity<ActionCatalog>> getByKey(@PathVariable String actionKey) {
        return this.service.findByActionKey(actionKey)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }
}
