package com.fincity.security.service;

import static com.fincity.security.service.AppService.*;
import static com.fincity.security.service.ClientService.*;

import java.security.SecureRandom;
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
import com.fincity.security.dto.AppProperty;
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
import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.security.dao.ClientUrlDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.ClientUrl;
import com.fincity.security.enums.ClientUrlType;
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
    /**
     * Must stay identical to the gateway's own copy in
     * {@code GatewayFilter.CACHE_NAME_GATEWAY_URL_CLIENT_APP_CODE}: the gateway
     * writes this cache and security evicts it, so a rename on one side alone
     * means minting or changing a client URL stops evicting anything and the
     * gateway serves a stale hostname resolution indefinitely.
     *
     * Renamed from "gatewayClientAppCode" when the cached value gained its third
     * element. See the gateway constant for why the old name could not be kept.
     */
    public static final String CACHE_NAME_GATEWAY_URL_CLIENT_APP_CODE = "gatewayClientAppCodeType";

    private static final String HTTPS = "https://";

    private static final String SLASH = "/";


    /** Draft hostnames are the only gate on unpublished work, so they get real entropy. */
    private static final SecureRandom DRAFT_HOST_RANDOM = new SecureRandom();

    /** 16 bytes, rendered lowercase hex: 128 bits in 32 characters. */
    private static final int DRAFT_HOST_RANDOM_BYTES = 16;

    /**
     * Draft hosts always live under Modlix's own domain, never under the app's.
     *
     * Constant on purpose. The point of a draft host is that the platform can serve
     * it: this domain is the one whose wildcard we hold, so putting every draft
     * under it means no DNS record and no certificate per app. An app on
     * ashwa.fincity.com still gets its draft here.
     */
    private static final String DRAFT_HOST_BASE_DOMAIN = ".modlix.com";

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

                    return clientService.isUserClientManageClient(ca, cu.getClientId());
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

                    return clientService.isUserClientManageClient(ca, entity.getClientId());
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

                (ca, app, cc, cId, prop) -> {
                    if (prop == null || prop.isEmpty())
                        return this.dao.getLatestClientUrlBasedOnAppAndClient(appCode, cId);

                    Map<String, AppProperty> clientProps = prop.get(cId);
                    AppProperty urlProp = clientProps != null ? clientProps.get(APP_PROP_URL) : null;
                    return urlProp != null
                            ? Mono.just(urlProp.getValue())
                            : this.dao.getLatestClientUrlBasedOnAppAndClient(appCode, cId);
                },

                (ca, app, cc, cId, prop, url) -> Mono.just(checkUrl(url))

        ).defaultIfEmpty("").contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientUrlService.getAppUrl"));
    }

    /**
     * Request-less variant of {@link #getAppUrl}: resolves the app URL for an explicit
     * (appCode, appId, clientId) with no security context. Used by event-triggered flows
     * (e.g. invoice emails raised from the payment webhook) that have no authenticated request.
     */
    public Mono<String> getAppUrlInternal(String appCode, ULong appId, ULong clientId) {
        return FlatMapUtil.flatMapMono(
                () -> this.appService.getProperties(clientId, appId, appCode, APP_PROP_URL),
                prop -> {
                    if (prop == null || prop.isEmpty())
                        return this.dao.getLatestClientUrlBasedOnAppAndClient(appCode, clientId);
                    Map<String, AppProperty> clientProps = prop.get(clientId);
                    AppProperty urlProp = clientProps != null ? clientProps.get(APP_PROP_URL) : null;
                    return urlProp != null ? Mono.just(urlProp.getValue())
                            : this.dao.getLatestClientUrlBasedOnAppAndClient(appCode, clientId);
                },
                (prop, url) -> Mono.just(checkUrl(url)))
                .defaultIfEmpty("")
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientUrlService.getAppUrlInternal"));
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

                (ca, hasAccess) -> this.clientService.isUserClientManageClient(ca, clientCode)
                        .filter(BooleanUtil::safeValueOf),

                (ca, hasAccess, hasClientAccess) -> this.dao.getClientUrls(appCode, clientCode))
                .switchIfEmpty(this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_WRITE_APPLICATION_ACCESS))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientUrlService.getClientUrls"));
    }

    // ── Draft surface URL ──────────────────────────────────────────

    /**
     * The app's draft hostname, if one has been minted.
     */
    public Mono<ClientUrl> getDraftUrl(String appCode) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                // The draft URL is a bearer credential for all unpublished work, so
                // reading it is gated exactly like minting it. Read access to the app
                // is not enough.
                ca -> ca.isSystemClient() ? Mono.just(Boolean.TRUE)
                        : this.appService.hasWriteAccess(appCode, ca.getClientCode()),

                (ca, hasAccess) -> BooleanUtil.safeValueOf(hasAccess)
                        ? this.clientService.getClientBy(ca.getClientCode())
                        : Mono.empty(),

                (ca, hasAccess, client) -> this.dao.getDraftUrl(appCode, client.getId()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientUrlService.getDraftUrl"));
    }

    /**
     * Mint or rotate the app's draft hostname.
     *
     * Rotate rather than accumulate: the existing DRAFT row's pattern is replaced,
     * which both keeps "at most one draft surface per (client, app)" true and gives
     * revocation of a shared draft link for free.
     *
     * Authorization is deliberately stricter than the rest of this service. Every
     * other write here gates on Authorities.Client_UPDATE plus a client-manages-client
     * check, and never looks at the app at all. A draft URL exposes unpublished work
     * for one specific app, and Client_UPDATE is a much broader authority than being
     * allowed to edit that app, so this also requires write access to the app itself.
     */
    public Mono<ClientUrl> mintDraftUrl(String appCode) {

        // No pre-flight check here any more, and that is deliberate rather than an
        // omission: the host is built from a constant domain and the environment
        // marker every other URL in this service already uses, so there is no
        // configuration that can be missing. A blank appCodeSuffix is the
        // production case, not an error.
        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> ca.isSystemClient() ? Mono.just(Boolean.TRUE)
                        : this.appService.hasWriteAccess(appCode, ca.getClientCode()),

                (ca, hasAccess) -> BooleanUtil.safeValueOf(hasAccess) ? this.clientService.getClientBy(
                        ca.getClientCode()) : Mono.empty(),

                (ca, hasAccess, client) -> this.dao.getDraftUrl(appCode, client.getId())
                        .map(existing -> existing.setUrlPattern(newDraftHost()))
                        .switchIfEmpty(Mono.fromSupplier(() -> {
                            ClientUrl fresh = new ClientUrl();
                            fresh.setClientId(client.getId())
                                    .setAppCode(appCode)
                                    .setUrlPattern(newDraftHost())
                                    .setUrlType(ClientUrlType.DRAFT);
                            return fresh;
                        })),

                (ca, hasAccess, client, url) -> url.getId() == null ? super.create(url) : super.update(url))

                // Without these the old hostname keeps resolving from cache after a
                // rotation, which would make revocation a lie.
                .flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URL))
                .flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URI))
                .flatMap(cacheService.evictAllFunction(CACHE_NAME_GATEWAY_URL_CLIENT_APP_CODE))
                .switchIfEmpty(this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_WRITE_APPLICATION_ACCESS))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientUrlService.mintDraftUrl"));
    }

    /**
     * A random hostname under Modlix's own domain: {@code d<32 hex><appCodeSuffix>.modlix.com}.
     *
     * Unguessable on purpose: the URL is the only thing gating access to the draft
     * surface for anyone the link is shared with, and UK1_URL_PATTERN keeps it
     * globally unique.
     *
     * The environment comes from {@code security.appCodeSuffix}, the marker this
     * service and IndexHTMLService already use, rather than a draft-specific key.
     * A second per-environment setting meaning almost the same thing is a setting
     * someone eventually forgets to move, and a draft host silently pointing at the
     * wrong environment is a bad way to find that out. Blank is production and
     * yields {@code d<hex>.modlix.com}.
     *
     * The app's own live URL has no bearing on this. Deriving from it was tried and
     * reverted: apps live on domains the platform holds no wildcard for
     * (sitezump.ai, fincity.com, cityville.in), two-label live URLs such as
     * theorempro.in produced a name directly under a public suffix, and a live URL
     * whose first label is the environment (dev.adzump.ai) produced a host on the
     * production apex.
     */
    String newDraftHost() {

        // NOT UniqueUtil.base36UUID(): that does
        // ByteBuffer.wrap(uuid.toString().getBytes()).getLong(), which reads the
        // first 8 CHARACTERS of the UUID's text form, i.e. 8 hex digits, so 32 bits
        // per call rather than the 128 the construction implies. This hostname is
        // the only gate on unpublished work, so it gets real entropy.
        //
        // Also not UniqueUtil.uniqueName(): that appends '_' after each name part,
        // and an underscore is illegal in a hostname under RFC 1123. Browsers
        // reject the name and no CA will issue for it, wildcard or not.
        byte[] raw = new byte[DRAFT_HOST_RANDOM_BYTES];
        DRAFT_HOST_RANDOM.nextBytes(raw);

        // Lowercase hex, deliberately. Base64url would be shorter but emits '-' and
        // '_', and an underscore is illegal in a hostname; lowercasing base64 would
        // also collapse case and throw away half the entropy. Hex is [0-9a-f] only,
        // so the label is valid by construction. The leading 'd' is not decoration:
        // a DNS label may not start with a digit, and hex starts with one 10 times
        // out of 16.
        StringBuilder label = new StringBuilder(1 + raw.length * 2).append('d');
        for (byte b : raw)
            label.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));

        return label + this.appCodeSuffix + DRAFT_HOST_BASE_DOMAIN;
    }
}
