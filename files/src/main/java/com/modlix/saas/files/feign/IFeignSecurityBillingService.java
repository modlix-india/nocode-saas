package com.modlix.saas.files.feign;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.modlix.saas.files.model.billing.ChargeRequest;
import com.modlix.saas.files.model.billing.MeteringInstruction;

/**
 * Cluster-only billing calls to security: fetch which (C, app, M) to meter for
 * stored GB and post the raw counts security then prices. Files is a blocking
 * servlet service, so these are synchronous Feign calls.
 */
@FeignClient(name = "security", contextId = "filesBillingSecurity")
public interface IFeignSecurityBillingService {

    @GetMapping("/api/security/internal/billing/instructions")
    List<MeteringInstruction> getInstructions(@RequestParam("action") String action);

    @PostMapping("/api/security/internal/billing/charge")
    Boolean charge(@RequestBody List<ChargeRequest> requests);
}
