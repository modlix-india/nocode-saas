package com.fincity.saas.core.feign;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.fincity.saas.core.model.billing.ChargeRequest;
import com.fincity.saas.core.model.billing.MeteringInstruction;

import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Cluster-only billing calls to security: fetch which (C, app, M) to meter and
 * post the raw counts security then prices.
 */
@ReactiveFeignClient(name = "security", qualifier = "coreBillingSecurity")
public interface IFeignSecurityBillingService {

    @GetMapping("/api/security/internal/billing/instructions")
    Flux<MeteringInstruction> getInstructions(@RequestParam("action") String action);

    @PostMapping("/api/security/internal/billing/charge")
    Mono<Boolean> charge(@RequestBody List<ChargeRequest> requests);
}
