package com.fincity.security.controller.billing;

import java.util.List;

import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.billing.AppBillingBundleDAO;
import com.fincity.security.dto.billing.AppBillingBundle;
import com.fincity.security.jooq.tables.records.SecurityAppBillingBundleRecord;
import com.fincity.security.service.billing.AppBillingBundleService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/billing-bundles")
public class AppBillingBundleController extends
        AbstractJOOQUpdatableDataController<SecurityAppBillingBundleRecord, ULong, AppBillingBundle, AppBillingBundleDAO, AppBillingBundleService> {

    @GetMapping("/config/{billingConfigId}")
    public Mono<ResponseEntity<List<AppBillingBundle>>> getByConfig(@PathVariable ULong billingConfigId) {
        return this.service.findByConfigId(billingConfigId).map(ResponseEntity::ok);
    }

    /**
     * Public buyer view: the ACTIVE bundles for the app hosted under the URL
     * (appCode + clientCode headers, gateway-filled). No id is taken from the
     * caller and the billing config is never exposed. permitAll - bundles are
     * pricing shown to users, including anonymous visitors.
     */
    @GetMapping("/serving")
    public Mono<ResponseEntity<List<AppBillingBundle>>> serving(ServerHttpRequest request) {
        String appCode = request.getHeaders().getFirst("appCode");
        String clientCode = request.getHeaders().getFirst("clientCode");
        return this.service.findServingByCodes(appCode, clientCode).map(ResponseEntity::ok);
    }
}
