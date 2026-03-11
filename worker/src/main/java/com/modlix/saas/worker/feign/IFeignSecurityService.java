package com.modlix.saas.worker.feign;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.modlix.saas.worker.dto.SSLCertificateRenewalResult;

@FeignClient(name = "security", contextId = "workerSecurityService")
public interface IFeignSecurityService {

    @PostMapping("/api/security/internal/tokens/cleanup")
    Map<String, Integer> cleanupTokens(@RequestParam("unusedDays") int unusedDays);

    @PostMapping("/api/security/ssl/internal/renew-certificates")
    SSLCertificateRenewalResult renewExpiringCertificates(
            @RequestParam(value = "daysBeforeExpiry", defaultValue = "30") int daysBeforeExpiry);
}
