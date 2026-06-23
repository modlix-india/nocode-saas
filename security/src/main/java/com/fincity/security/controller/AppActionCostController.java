package com.fincity.security.controller;

import java.util.List;

import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.billing.AppActionCostDAO;
import com.fincity.security.dto.billing.AppActionCost;
import com.fincity.security.jooq.tables.records.SecurityAppActionCostRecord;
import com.fincity.security.service.billing.AppActionCostService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/action-costs")
public class AppActionCostController extends AbstractJOOQUpdatableDataController<SecurityAppActionCostRecord,
        ULong, AppActionCost, AppActionCostDAO, AppActionCostService> {

    public AppActionCostController(AppActionCostService service) {
        this.service = service;
    }

    @GetMapping("/config/{billingConfigId}")
    public Mono<ResponseEntity<List<AppActionCost>>> getByConfig(@PathVariable ULong billingConfigId) {
        return this.service.findByConfigId(billingConfigId).map(ResponseEntity::ok);
    }
}
