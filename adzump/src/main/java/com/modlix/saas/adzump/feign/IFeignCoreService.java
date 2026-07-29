package com.modlix.saas.adzump.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Blocking Feign client to the Core service (J2 - connections).
 *
 * Mirrors entity-processor's IFeignCoreService.getConnectionOAuth2Token in the
 * commons2 blocking IFeignSecurityService style. The endpoint is an /internal/
 * one: it is gateway-gated, so only the clientCode + appCode headers are
 * forwarded (no bearer). Core owns the connections and their refresh; adzump
 * only reads a usable token.
 */
@FeignClient(name = "core", contextId = "adzumpCoreService")
public interface IFeignCoreService {

    @GetMapping("${core.connection.getOAuth2Token:/api/core/connections/internal/oauth2/token/{connectionName}}")
    String getConnectionOAuth2Token(
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String appCode,
            @PathVariable("connectionName") String connectionName); // GOOGLE_API | META_API
}
