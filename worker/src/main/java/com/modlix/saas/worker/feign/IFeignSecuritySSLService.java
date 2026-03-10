package com.modlix.saas.worker.feign;

import com.modlix.saas.worker.dto.SSLCertificateRenewalResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "security-ssl")
public interface IFeignSecuritySSLService {

    @PostMapping("/api/security/ssl/internal/renew-certificates")
    SSLCertificateRenewalResult renewExpiringCertificates(
            @RequestParam(value = "daysBeforeExpiry", defaultValue = "30") int daysBeforeExpiry);
}
