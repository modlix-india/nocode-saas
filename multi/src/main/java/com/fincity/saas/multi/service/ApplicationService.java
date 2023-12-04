package com.fincity.saas.multi.service;

import java.util.Map;
import java.util.Optional;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.dto.App;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.multi.dto.MultiApp;
import com.fincity.saas.multi.dto.MultiAppUpdate;
import com.fincity.saas.multi.fiegn.IFeignCoreService;
import com.fincity.saas.multi.fiegn.IFeignUIService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ApplicationService {

    private final IFeignSecurityService securityService;
    private final IFeignCoreService coreService;
    private final IFeignUIService uiService;
    private final MultiMessageResourceService messageResourceService;

    public ApplicationService(IFeignSecurityService securityService, IFeignCoreService coreService,
            IFeignUIService uiService, MultiMessageResourceService messageResourceService) {
        this.securityService = securityService;
        this.coreService = coreService;
        this.uiService = uiService;
        this.messageResourceService = messageResourceService;
    }

    public Mono<Map<String, Map<String, Object>>> transport(
            String forwardedHost,
            String forwardedPort,
            String clientCode,
            String headerAppCode,
            String appCode) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.securityService.makeTransport(ca.getAccessToken(), forwardedHost,
                        forwardedPort, clientCode,
                        headerAppCode, appCode),

                (ca, security) -> this.coreService.makeTransport(ca.getAccessToken(), forwardedHost,
                        forwardedPort,
                        clientCode, headerAppCode,
                        Map.of("appCode", appCode, "clientCode", ca.getClientCode())),

                (ca, security, core) -> this.uiService.makeTransport(ca.getAccessToken(), forwardedHost,
                        forwardedPort,
                        clientCode, headerAppCode,
                        Map.of("appCode", appCode, "clientCode", ca.getClientCode())),

                (ca, security, core, ui) -> Mono
                        .just(Map.<String, Map<String, Object>>of("security", security, "core", core, "ui", ui)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ApplicationService.transport"));
    }

    public Mono<App> createApplication(
            String forwardedHost,
            String forwardedPort,
            String clientCode,
            String headerAppCode, MultiApp application) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> {

                    if (!SecurityContextUtil.hasAuthority("Authorities.Application_CREATE",
                            ca.getAuthorities())) {
                        return this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                MultiMessageResourceService.FORBIDDEN_CREATE,
                                "Application");
                    }

                    if (application.getAppId() == null)
                        return Mono.just(Optional.<App>empty());

                    return this.securityService
                            .getAppById(ca.getAccessToken(), forwardedHost, forwardedPort,
                                    clientCode, headerAppCode,
                                    application.getAppId().toString())
                            .map(Optional::of);
                },

                (ca, app) -> {

                    if (!app.isEmpty())
                        return Mono.just(app.get());

                    if (StringUtil.safeIsBlank(application.getAppAccessType())
                            || "OWN".equals(application.getAppAccessType())) {
                        application.setAppAccessType("OWN");
                    }

                    application.setClientId(ULongUtil.valueOf(ca.getUser().getClientId()));

                    App secApp = new App();
                    secApp.setAppCode(application.getAppCode());
                    secApp.setAppName(application.getAppName());
                    secApp.setAppType(application.getAppType());
                    secApp.setAppAccessType(application.getAppAccessType());
                    secApp.setClientId(
                            application.getClientId() == null ? ca.getUser().getClientId()
                                    : application.getClientId().toBigInteger());

                    return this.securityService
                            .createApp(ca.getAccessToken(), forwardedHost, forwardedPort,
                                    clientCode, headerAppCode, secApp)
                            .flatMap(e -> this.addDefinition(ca.getAccessToken(),
                                    forwardedHost, forwardedPort,
                                    clientCode, headerAppCode, application, e));
                });
    }

    public Mono<App> addDefinition(String accessToken,
            String forwardedHost,
            String forwardedPort,
            String clientCode,
            String headerAppCode,
            MultiApp application,
            App app) {

        boolean hasDefinition = StringUtil.safeIsBlank(application.getTransportDefinitionURL())
                && application.getTransportDefinition() == null
                || application.getTransportDefinition().isEmpty();

        if (!hasDefinition)
            return Mono.just(app);

        return FlatMapUtil.flatMapMonoWithNull(
                () -> application.getTransportDefinition() == null
                        || application.getTransportDefinition().isEmpty()
                                ? WebClient.create(
                                        application.getTransportDefinitionURL())
                                        .get().retrieve()
                                        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                                        })
                                : Mono.just(application.getTransportDefinition()),

                definiton -> this.securityService.createAndApplyTransport(accessToken,
                        forwardedHost, forwardedPort,
                        clientCode, headerAppCode,
                        definiton.get("security")),

                (definition, s) -> this.coreService.createAndApplyTransport(accessToken, forwardedHost, forwardedPort,
                        clientCode, headerAppCode, true, app.getAppCode(), definition.get("core")),

                (definition, s, c) -> this.uiService.createAndApplyTransport(accessToken, forwardedHost, forwardedPort,
                        clientCode, headerAppCode, true, app.getAppCode(), definition.get("ui")),

                (definition, s, c, u) -> Mono.just(app));
    }

    public Mono<Boolean> updateApplication(String forwardedHost,
            String forwardedPort, String clientCode, String headerAppCode, MultiAppUpdate application) {

        return FlatMapUtil.flatMapMonoWithNull(
                () -> application.getTransportDefinition() == null
                        || application.getTransportDefinition().isEmpty()
                                ? WebClient.create(
                                        application.getTransportDefinitionURL())
                                        .get().retrieve()
                                        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                                        })
                                : Mono.just(application.getTransportDefinition()),

                definition -> SecurityContextUtil.getUsersContextAuthentication()
                        .map(ContextAuthentication::getAccessToken),

                (definiton, accessToken) -> this.securityService.createAndApplyTransport(accessToken,
                        forwardedHost, forwardedPort,
                        clientCode, headerAppCode,
                        definiton.get("security")),

                (definition, accessToken, s) -> this.coreService.createAndApplyTransport(accessToken, forwardedHost,
                        forwardedPort,
                        clientCode, headerAppCode, application.getIsBaseUpdate(), application.getAppCode(),
                        definition.get("core")),

                (definition, accessToken, s, c) -> this.uiService.createAndApplyTransport(accessToken, forwardedHost,
                        forwardedPort,
                        clientCode, headerAppCode, application.getIsBaseUpdate(), application.getAppCode(),
                        definition.get("ui")),

                (definition, accessToken, s, c, u) -> Mono.just(true));
    }

}
