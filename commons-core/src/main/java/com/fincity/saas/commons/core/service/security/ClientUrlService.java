package com.fincity.saas.commons.core.service.security;

import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ClientUrlService {

    private IFeignSecurityService securityService;

    @Autowired
    private void setFeignSecurityService(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    public Mono<String> getAppUrl(String appCode, String clientCode) {
        return securityService.getAppUrl(appCode, clientCode);
    }
}
