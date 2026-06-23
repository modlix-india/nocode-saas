package com.fincity.security.controller;

import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.billing.AppBillingConfigDAO;
import com.fincity.security.dto.billing.AppBillingConfig;
import com.fincity.security.jooq.tables.records.SecurityAppBillingConfigRecord;
import com.fincity.security.service.billing.AppBillingConfigService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/billing-configs")
public class AppBillingConfigController extends AbstractJOOQUpdatableDataController<SecurityAppBillingConfigRecord,
        ULong, AppBillingConfig, AppBillingConfigDAO, AppBillingConfigService> {

    public AppBillingConfigController(AppBillingConfigService service) {
        this.service = service;
    }

    @GetMapping("/app/{appId}/client/{clientId}")
    public Mono<ResponseEntity<AppBillingConfig>> getByAppAndClient(@PathVariable ULong appId,
            @PathVariable ULong clientId) {
        return this.service.findByAppIdAndClientId(appId, clientId)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @GetMapping("/app/{appId}")
    public Mono<ResponseEntity<java.util.List<AppBillingConfig>>> getByApp(@PathVariable ULong appId) {
        return this.service.findByApp(appId).map(ResponseEntity::ok);
    }
}
