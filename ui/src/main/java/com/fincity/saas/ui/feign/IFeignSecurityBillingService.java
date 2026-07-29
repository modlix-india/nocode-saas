package com.fincity.saas.ui.feign;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fincity.saas.ui.model.billing.HostingDecision;

import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

/**
 * Cluster-only billing call to security for the app/site hosting gate: given the
 * URL's app/client, returns which app/client to actually serve (swapped to the
 * configured suspend app/client when the builder wallet is suspended).
 */
@ReactiveFeignClient(name = "security", qualifier = "uiBillingSecurity")
public interface IFeignSecurityBillingService {

    @GetMapping("/api/security/internal/billing/hosting")
    Mono<HostingDecision> checkHosting(@RequestParam("urlAppCode") String urlAppCode,
            @RequestParam("urlClientCode") String urlClientCode);
}
