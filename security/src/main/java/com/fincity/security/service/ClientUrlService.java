package com.fincity.security.service;

import static com.fincity.security.service.AppService.APP_PROP_URL;
import static com.fincity.security.service.ClientService.CACHE_NAME_CLIENT_URL;

import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dao.ClientUrlDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.ClientUrl;
import com.fincity.security.jooq.tables.records.SecurityClientUrlRecord;

import lombok.NonNull;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ClientUrlService
        extends AbstractJOOQUpdatableDataService<SecurityClientUrlRecord, ULong, ClientUrl, ClientUrlDAO> {

    private static final String URL_PATTERN = "urlPattern";

    private static final String CLIENT_URL = "Client URL";

    private final CacheService cacheService;

    private final SecurityMessageResourceService msgService;

    private final ClientService clientService;

    private final AppService appService;

    @Value("${security.appCodeSuffix:}")
    private String appCodeSuffix;

    private static final String CACHE_NAME_CLIENT_URI = "uri";

    // This is used in gateway
    private static final String CACHE_NAME_GATEWAY_URL_CLIENT_APP_CODE = "gatewayClientAppCode";

    private static final String HTTPS = "https://";

    private static final String SLASH = "/";

    public ClientUrlService(CacheService cacheService, SecurityMessageResourceService msgService,
                            ClientService clientService, AppService appService) {

        this.cacheService = cacheService;
        this.msgService = msgService;
        this.clientService = clientService;
        this.appService = appService;
    }

    @PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
    @Override
    public Mono<ClientUrl> read(ULong id) {

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> super.read(id),

                        (ca, cu) -> {

                            if (ca.isSystemClient() || ca.getUser().getClientId().equals(cu.getClientId().toBigInteger()))
                                return Mono.just(true);

                            return clientService.isBeingManagedBy(ULong.valueOf(ca.getUser().getClientId()), cu.getClientId());
                        },

                        (ca, cu, hasAccess) -> {
                            if (BooleanUtil.safeValueOf(hasAccess))
                                return Mono.just(cu);
                            return Mono.empty();
                        }).contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientUrlService.read"))
                .switchIfEmpty(msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                        AbstractMessageService.OBJECT_NOT_FOUND, CLIENT_URL, id));
    }

    @PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
    @Override
    public Mono<Page<ClientUrl>> readPageFilter(Pageable pageable, AbstractCondition condition) {

        return super.readPageFilter(pageable, condition);
    }

    @PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
    @Override
    public Mono<ClientUrl> create(ClientUrl entity) {

        entity.setUrlPattern(trimBackSlash(entity.getUrlPattern()));

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> {

                            if (ca.isSystemClient() || entity.getClientId() == null
                                    || ca.getUser().getClientId().equals(entity.getClientId().toBigInteger()))
                                return Mono.just(true);

                            return clientService.isBeingManagedBy(ULong.valueOf(ca.getUser().getClientId()),
                                    entity.getClientId());
                        },

                        (ca, hasAccess) -> BooleanUtil.safeValueOf(hasAccess) ? Mono.just(entity) : Mono.empty(),

                        (ca, hasAccess, ent) -> {

                            ULong clientId = ULong.valueOf(ca.getUser().getClientId());

                            if (ent.getClientId() == null)
                                ent.setClientId(clientId);

                            return super.create(ent);
                        }).contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientUrlService.read"))
                .flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URL))
                .flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URI))
                .flatMap(cacheService.evictAllFunction(CACHE_NAME_GATEWAY_URL_CLIENT_APP_CODE))
                .flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE))
                .flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT));
    }

    @PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
    @Override
    public Mono<ClientUrl> update(ClientUrl entity) {

        entity.setUrlPattern(trimBackSlash(entity.getUrlPattern()));

        return super.update(entity).flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URL))
                .flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URI))
                .flatMap(cacheService.evictAllFunction(CACHE_NAME_GATEWAY_URL_CLIENT_APP_CODE))
                .flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE))
                .flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT));
    }

    @PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
    @Override
    public Mono<ClientUrl> update(ULong key, Map<String, Object> updateFields) {

        updateFields.computeIfPresent(URL_PATTERN, (k, v) -> trimBackSlash(v.toString()));

        return super.update(key, updateFields).flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URL))
                .flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URI))
                .flatMap(cacheService.evictAllFunction(CACHE_NAME_GATEWAY_URL_CLIENT_APP_CODE))
                .flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE))
                .flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT));
    }

    @PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
    @Override
    public Mono<Integer> delete(ULong id) {

        return this.read(id).flatMap(e -> super.delete(id))
                .flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URL))
                .flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URI))
                .flatMap(cacheService.evictAllFunction(CACHE_NAME_GATEWAY_URL_CLIENT_APP_CODE))
                .flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE))
                .flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT));
    }

    @PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
    @Override
    protected Mono<ClientUrl> updatableEntity(ClientUrl entity) {
        return this.read(entity.getId()).map(e -> e.setUrlPattern(entity.getUrlPattern()));
    }

    @Override
    protected Mono<ULong> getLoggedInUserId() {

        return SecurityContextUtil.getUsersContextUser().map(ContextUser::getId).map(ULong::valueOf);
    }

    public Mono<List<String>> getUrlsBasedOnApp(@NonNull String appCode, String suffix) {

        if (StringUtil.safeIsBlank(appCode))
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    SecurityMessageResourceService.MANDATORY_APP_CODE);

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> Mono.just(ULongUtil.valueOf(ca.getUser().getClientId())),

                (ca, clientId) -> ca.isSystemClient() ? this.dao.getClientUrlsBasedOnAppAndClient(appCode, null)
                        : this.dao.getClientUrlsBasedOnAppAndClient(appCode, clientId),

                (ca, clientId, urlList) -> this.appService.getAppByCode(appCode),

                (ca, clientId, urlList, app) -> {

                    if (!StringUtil.safeIsBlank(suffix)) {

                        if (app.getClientId().equals(clientId))
                            urlList.add(HTTPS + appCode + appCodeSuffix + suffix + SLASH);
                        else
                            urlList.add(HTTPS + appCode + appCodeSuffix + suffix + SLASH + ca.getClientCode() + SLASH
                                    + "page" + SLASH);
                    }

                    return Mono.just(urlList);
                }

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientUrlService.getUrlsBasedOnApp"));

    }

    public Mono<String> getAppUrl(String appCode, String clientCode) {

        if (StringUtil.safeIsBlank(appCode))
            return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    SecurityMessageResourceService.MANDATORY_APP_CODE);

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> appService.getAppByCode(appCode),

                (ca, app) -> {
                    if (StringUtil.safeIsBlank(clientCode)) {
                        return Mono.just(
                                ca.getUrlClientCode() != null ? ca.getUrlClientCode() : ca.getLoggedInFromClientCode());
                    }
                    return Mono.just(clientCode);
                },

                (ca, app, cc) -> clientService.getClientBy(cc).map(Client::getId),

                (ca, app, cc, cId) -> appService.getProperties(cId, app.getId(), appCode, APP_PROP_URL),

                (ca, app, cc, cId, prop) -> prop == null || prop.isEmpty()
                        ? this.dao.getLatestClientUrlBasedOnAppAndClient(appCode, cId)
                        : Mono.just(prop.get(cId).get(APP_PROP_URL).getValue()),

                (ca, app, cc, cId, prop, url) -> Mono.just(checkUrl(url))

        ).defaultIfEmpty("").contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientUrlService.getAppUrl"));
    }

    public Mono<Boolean> checkSubDomainAvailabilityWithSuffix(String subDomain) {

        if (StringUtil.safeIsBlank(subDomain))
            return Mono.just(false);

        return this.dao.checkSubDomainAvailability(subDomain);
    }

    public Mono<Boolean> checkSubDomainAvailability(String subDomain, String fullURL) {

        return FlatMapUtil.flatMapMono(

                () -> this.checkSubDomainAvailabilityWithSuffix(fullURL),

                exists -> {
                    if (BooleanUtil.safeValueOf(exists))
                        return Mono.just(exists);

                    return this.appService.getAppByCode(subDomain).map(e -> true).defaultIfEmpty(false);
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientUrlService.checkSubDomainAvailability"));
    }

    public Mono<ClientUrl> createForRegistration(ClientUrl entity) {

        entity.setUrlPattern(trimBackSlash(entity.getUrlPattern()));

        return super.create(entity).flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URL))
                .flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URI))
                .flatMap(cacheService.evictAllFunction(CACHE_NAME_GATEWAY_URL_CLIENT_APP_CODE))
                .flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE))
                .flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT));
    }

    private String trimBackSlash(String str) {

        if (StringUtil.safeIsBlank(str))
            return str;

        String nStr = str.trim();

        if (!nStr.endsWith("/"))
            return nStr;

        int endIndex = str.length() - 1;

        while (endIndex >= 0 && str.charAt(endIndex) == '/')
            endIndex--;

        return nStr.substring(0, endIndex + 1);
    }

    private String checkUrl(String url) {

        if (StringUtil.safeIsBlank(url))
            return url;

        String nStr = trimBackSlash(url);

        return !nStr.startsWith(HTTPS) ? HTTPS + nStr : nStr;
    }

    public Mono<List<ClientUrl>> getClientUrls(String appCode, String clientCode) {
        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> this.appService.hasReadAccess(appCode, ca.getClientCode()).filter(BooleanUtil::safeValueOf),

                        (ca, hasAccess) -> this.clientService.isBeingManagedBy(ca.getClientCode(), clientCode).filter(BooleanUtil::safeValueOf),

                        (ca, hasAccess, hasClientAccess) -> this.dao.getClientUrls(appCode, clientCode)
                ).switchIfEmpty(this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg), SecurityMessageResourceService.FORBIDDEN_WRITE_APPLICATION_ACCESS))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientUrlService.getClientUrls"));
    }
}
