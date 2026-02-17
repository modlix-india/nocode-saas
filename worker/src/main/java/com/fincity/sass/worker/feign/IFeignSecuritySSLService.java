package com.fincity.sass.worker.feign;

import com.fincity.sass.worker.dto.SSLCertificateRenewalResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "security")
public interface IFeignSecuritySSLService {

    @PostMapping("/api/security/ssl/internal/renew-certificates")
    SSLCertificateRenewalResult renewExpiringCertificates(
            @RequestParam(value = "daysBeforeExpiry", defaultValue = "30") int daysBeforeExpiry);
}
