package com.fincity.security.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dao.SSOBundleDAO;
import com.fincity.security.dto.SSOBundle;
import com.fincity.security.jooq.tables.records.SecurityAppSsoBundleRecord;

import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class SSOBundleService extends AbstractJOOQUpdatableDataService<SecurityAppSsoBundleRecord, ULong, SSOBundle, SSOBundleDAO> {

    private final AppService appService;
    private final ClientService clientService;
    private final SecurityMessageResourceService messageService;
    private final CacheService cacheService;

    private static final String CACHE_NAME_BUNDLE = "ssoBundle";

    public static final String SSO_TOKEN = "SSOToken";

    public SSOBundleService(AppService appService, ClientService clientService, SecurityMessageResourceService messageService, CacheService cacheService) {
        this.appService = appService;
        this.clientService = clientService;
        this.messageService = messageService;
        this.cacheService = cacheService;
    }

    @PreAuthorize("hasAuthority('Authorities.Application_CREATE')")
    @Override
    public Mono<SSOBundle> create(SSOBundle entity) {
        return this.hasAccessToBundle(entity).flatMap(super::create).flatMap(this::fillBundledApps).flatMap(this::evictCache);
    }

    @PreAuthorize("hasAuthority('Authorities.Application_CREATE')")
    @Override
    public Mono<SSOBundle> update(SSOBundle entity) {
        return this.hasAccessToBundle(entity).flatMap(super::update).flatMap(this::fillBundledApps).flatMap(this::evictCache);
    }

    @PreAuthorize("hasAuthority('Authorities.Application_CREATE')")
    @Override
    public Mono<Integer> delete(ULong id) {

        return this.read(id).flatMap(this::hasAccessToBundle).flatMap(bundle -> super.delete(id)
                .flatMap(deleted -> this.evictCache(bundle).map(x -> deleted)));
    }

    private Mono<SSOBundle> evictCache(SSOBundle bundle) {
        if (bundle.getApps() == null || bundle.getApps().isEmpty()) return Mono.just(bundle);

        return Flux.fromIterable(bundle.getApps())
                .flatMap(app -> this.cacheService.evict(CACHE_NAME_BUNDLE, bundle.getClientCode(), app.getAppCode()))
                .then(Mono.just(bundle));
    }

    public Mono<SSOBundle> hasAccessToBundle(SSOBundle entity) {
        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> this.clientService.isBeingManagedBy(ca.getClientCode(), entity.getClientCode()).filter(BooleanUtil::safeValueOf),

                        (ca, managed) -> {
                            if (entity.getApps() == null || entity.getApps().isEmpty()) return Mono.just(entity);

                            return Flux.fromIterable(entity.getApps()).map(SSOBundle.SSOBundledApp::getAppCode)
                                    .flatMap(appCode -> this.appService.hasWriteAccess(appCode, entity.getClientCode()))
                                    .all(BooleanUtil::safeValueOf).map(BooleanUtil::safeValueOf).map(x -> entity);
                        }
                ).switchIfEmpty(this.messageService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg), SecurityMessageResourceService.FORBIDDEN_WRITE_APPLICATION_ACCESS))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "SSOBundleService.hasAccessToBundle"));
    }

    public Mono<Boolean> evictCache(String clientCode, String appCode) {
        return FlatMapUtil.flatMapMono(
                () -> this.readBundles(clientCode, appCode),

                bundles -> Flux.fromIterable(bundles).flatMap(this::evictCache).collectList().thenReturn(true)
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "SSOBundleService.evictCache"));
    }

    // Since there is no restriction for bundle name, all fields can be changed.
    @Override
    protected Mono<SSOBundle> updatableEntity(SSOBundle entity) {
        return Mono.just(entity);
    }

    private Mono<SSOBundle> fillBundledApps(SSOBundle bundle) {

        if (bundle.getApps() == null || bundle.getApps().isEmpty()) return Mono.just(bundle);

        return FlatMapUtil.flatMapMono(
                () -> Flux.fromIterable(bundle.getApps())
                        .flatMap(ba -> this.appService.getAppByCode(ba.getAppCode()).map(ba::setApp))
                        .collectMap(SSOBundle.SSOBundledApp::getAppUrlId),
                setBundle -> this.clientService.readClientURLs(bundle.getClientCode(), setBundle.keySet()),
                (setBundle, urlMap) -> {
                    setBundle.values().forEach(app -> app.setUrl(urlMap.get(app.getAppUrlId())));
                    return Mono.just(bundle);
                }
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "SSOBundleService.fillBundledApps"));
    }

    public Mono<ArrayList<SSOBundle>> readBundles(String clientCode, String appCode) {
        return this.cacheService.cacheValueOrGet(CACHE_NAME_BUNDLE,
                        () -> this.dao.readByClientCodeAppcode(clientCode, appCode), clientCode, appCode)
                .flatMap(bundles -> Flux.fromIterable(bundles).flatMap(this::fillBundledApps).collectList().map(ArrayList::new));
    }

    public Mono<ServerHttpResponse> makeSSOTokens(
            String ipAddress,
            LocalDateTime accessTokenExpiryAt,
            ULong userId,
            String appCode,
            String clientCode,
            ServerHttpResponse response
    ) {

        String token = UUID.randomUUID().toString().replace("-", "");

        return this.makeSSOTokens(
                () -> this.dao.makeToken(ipAddress, token, userId, accessTokenExpiryAt),
                token, accessTokenExpiryAt, appCode, clientCode, response);
    }

    private Mono<ServerHttpResponse> makeSSOTokens(
            Supplier<Mono<Boolean>> dbSupplier,
            String token,
            LocalDateTime accessTokenExpiryAt,
            String appCode,
            String clientCode,
            ServerHttpResponse response
    ) {

        return FlatMapUtil.flatMapMono(
                () -> this.readBundles(clientCode, appCode),

                bundles -> Mono.just(bundles.stream().flatMap(bundle -> bundle.getApps().stream())
                        .map(SSOBundle.SSOBundledApp::getUrl)
                        .filter(url -> !StringUtil.safeIsBlank(url)).map(this::cleanURL).toList()),

                (bundles, domainNames) -> {
                    if (bundles.isEmpty()) return Mono.just(response);

                    return dbSupplier.get().map(created -> {
                        if (!created) return response;

                        long seconds = LocalDateTime.now().until(accessTokenExpiryAt, ChronoUnit.SECONDS);

                        domainNames.stream().map(e -> ResponseCookie.from(SSO_TOKEN, token)
                                        .domain(e).path("/")
                                        .httpOnly(true).secure(true)
                                        .maxAge(seconds).build())
                                .forEach(response::addCookie);

                        return response;
                    });
                }
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "AuthenticationService.makeSSOTokens"));
    }

    private String cleanURL(String url) {

        url = url.trim();
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        if (url.startsWith("https://")) url = url.substring(8);
        else if (url.startsWith("http://")) url = url.substring(9);

        return url;
    }

    public Mono<ServerHttpResponse> updateExpiry(String token, LocalDateTime accessTokenExpiryAt,
                                                 String appCode,
                                                 String clientCode,
                                                 ServerHttpResponse response) {

        String newToken = UUID.randomUUID().toString().replace("-", "");

        return this.makeSSOTokens(
                () -> this.dao.updateExpiry(token, newToken, accessTokenExpiryAt),
                token, accessTokenExpiryAt, appCode, clientCode, response
        );
    }

    public Mono<Boolean> deleteTokens(String token) {
        return this.dao.deleteToken(token);
    }
}
