package com.modlix.saas.worker.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "core", contextId = "workerCoreService")
public interface IFeignCoreService {

    @PostMapping("/api/core/internal/tokens/cleanup")
    Integer cleanupExpiredCoreTokens();
}
