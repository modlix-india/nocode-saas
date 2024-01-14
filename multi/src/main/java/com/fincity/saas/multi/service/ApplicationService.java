package com.fincity.saas.multi.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
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
import reactor.util.function.Tuple2;

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
                        .just(Map.<String, Map<String, Object>>of("security", security, "core",
                                core, "ui", ui)))
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
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ApplicationService.createApplication"));
    }

    public Mono<App> addDefinition(String accessToken,
            String forwardedHost,
            String forwardedPort,
            String clientCode,
            String headerAppCode,
            MultiApp application,
            App app) {

        boolean hasDefinition = !StringUtil.safeIsBlank(application.getTransportDefinitionURL())
                || (application.getTransportDefinition() != null
                        && !application.getTransportDefinition().isEmpty());

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

                definition -> this.securityService
                        .findBaseClientCodeForOverride(accessToken, forwardedHost,
                                forwardedPort, clientCode,
                                headerAppCode, application.getAppCode())
                        .map(Tuple2::getT1),

                (definition, cc) -> this.securityService.createAndApplyTransport(accessToken,
                        forwardedHost, forwardedPort,
                        clientCode, headerAppCode,
                        makeAppCodeChanges((Map<String, Object>) definition.get("security"),
                                application.getAppCode(),
                                cc)),

                (definition, cc, s) -> this.coreService
                        .createAndApplyTransport(accessToken, forwardedHost,
                                forwardedPort,
                                clientCode, headerAppCode, true, app.getAppCode(),
                                makeAppCodeChanges(
                                        (Map<String, Object>) definition
                                                .get("core"),
                                        application.getAppCode(), cc))
                        .map(e -> true),

                (definition, cc, s, c) -> this.uiService
                        .createAndApplyTransport(accessToken, forwardedHost,
                                forwardedPort,
                                clientCode, headerAppCode, true, app.getAppCode(),
                                makeAppCodeChanges(
                                        (Map<String, Object>) definition
                                                .get("ui"),
                                        application.getAppCode(), cc))
                        .map(e -> true),

                (definition, cc, s, c, u) -> Mono.just(app))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ApplicationService.addDefinition"));
    }

    public Mono<Boolean> updateApplication(String forwardedHost,
            String forwardedPort, String clientCode, String headerAppCode, MultiAppUpdate application) {

        return FlatMapUtil.flatMapMonoWithNull(
                () -> application.getTransportDefinition() == null
                        || application.getTransportDefinition().isEmpty()
                                ? WebClient.builder().exchangeStrategies(
                                        ExchangeStrategies.builder().codecs(
                                                clientCodecConfigurer -> clientCodecConfigurer
                                                        .defaultCodecs()
                                                        .maxInMemorySize(
                                                                50 * 1024 * 1024))
                                                .build())
                                        .baseUrl(
                                                application.getTransportDefinitionURL())
                                        .build().get().retrieve()
                                        .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                                        })

                                : Mono.just(application.getTransportDefinition()),

                definition -> SecurityContextUtil.getUsersContextAuthentication()
                        .map(ContextAuthentication::getAccessToken),

                (definition, accessToken) -> this.securityService
                        .findBaseClientCodeForOverride(accessToken, forwardedHost,
                                forwardedPort, clientCode,
                                headerAppCode, application.getAppCode())
                        .map(Tuple2::getT1),

                (definition, accessToken, cc) -> this.securityService.createAndApplyTransport(
                        accessToken,
                        forwardedHost, forwardedPort,
                        clientCode, headerAppCode,
                        makeAppCodeChanges((Map<String, Object>) definition.get("security"),
                                application.getAppCode(),
                                cc)),

                (definition, accessToken, cc, s) -> this.coreService.createAndApplyTransport(
                        accessToken,
                        forwardedHost,
                        forwardedPort,
                        clientCode, headerAppCode, application.getIsBaseUpdate(),
                        application.getAppCode(),
                        makeAppCodeChanges((Map<String, Object>) definition.get("core"),
                                application.getAppCode(), cc)),

                (definition, accessToken, cc, s, c) -> this.uiService.createAndApplyTransport(
                        accessToken,
                        forwardedHost,
                        forwardedPort,
                        clientCode, headerAppCode, application.getIsBaseUpdate(),
                        application.getAppCode(),
                        makeAppCodeChanges((Map<String, Object>) definition.get("ui"),
                                application.getAppCode(), cc)),

                (definition, accessToken, cc, s, c, u) -> Mono.just(true))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ApplicationService.updateApplication"));
    }

    private Map<String, Object> makeAppCodeChanges(Map<String, Object> map, String appCode, String clientCode) {

        if (map == null || map.isEmpty())
            return map;

        map.put("appCode", appCode);
        map.put("clientCode", clientCode);

        if (map.get("objects") instanceof List<?> lst)
            for (Object e : lst) {
                if (e instanceof Map<?, ?> exMap && exMap.get("data") instanceof Map<?, ?> dataMap) {
                    Map<String, Object> inMap = (Map<String, Object>) dataMap;
                    inMap.put("appCode", appCode);
                    inMap.put("clientCode", clientCode);

                    if ("Application".equals(exMap.get("objectType")) || "Filler".equals(exMap.get("objectType"))) {
                        inMap.put("name", appCode);
                    }
                }
            }

        return map;
    }
}
