package com.fincity.saas.multi.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.multi.fiegn.IFeignCoreService;
import com.fincity.saas.multi.fiegn.IFeignUIService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/multi/application")
public class ApplicationController {

    private final IFeignSecurityService securityService;
    private final IFeignCoreService coreService;
    private final IFeignUIService uiService;

    public ApplicationController(IFeignSecurityService securityService, IFeignCoreService coreService,
            IFeignUIService uiService) {
        this.securityService = securityService;
        this.coreService = coreService;
        this.uiService = uiService;
    }

    @GetMapping("/transport")
    public Mono<Map<String, Object>> transport(
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String headerAppCode,
            @RequestParam String appCode) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.securityService.makeTransport(ca.getAccessToken(), forwardedHost, forwardedPort, clientCode,
                        headerAppCode, appCode),

                (ca, security) -> this.coreService.makeTransport(ca.getAccessToken(), forwardedHost, forwardedPort,
                        clientCode, headerAppCode,
                        Map.of("appCode", appCode, "clientCode", ca.getClientCode())),

                (ca, security, core) -> this.uiService.makeTransport(ca.getAccessToken(), forwardedHost, forwardedPort,
                        clientCode, headerAppCode,
                        Map.of("appCode", appCode, "clientCode", ca.getClientCode())),

                (ca, security, core, ui) -> Mono.just(Map.of("security", security, "core", core, "ui", ui)));
    }

}
