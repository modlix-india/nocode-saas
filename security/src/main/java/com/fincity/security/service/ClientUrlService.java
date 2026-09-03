package com.fincity.security.service;

import static com.fincity.security.service.AppService.*;
import static com.fincity.security.service.ClientService.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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
import com.fincity.saas.commons.security.model.ClientUrlPattern;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.security.dao.ClientUrlDAO;
import com.fincity.security.dao.DraftTokenDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.ClientUrl;
import com.fincity.security.dto.DraftToken;
import com.fincity.security.enums.ClientUrlType;
import com.fincity.security.jooq.tables.records.SecurityClientUrlRecord;
import com.fincity.security.model.DraftTokenResponse;

import lombok.NonNull;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

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

    /**
     * The label prefix that marks a hostname as an editing session's grant.
     *
     * A hyphen rather than a bare letter so the shape cannot collide with an app
     * whose own name begins with t, and the leading character is still a letter,
     * which a DNS label requires.
     */
    private static final String DRAFT_TOKEN_HOST_PREFIX = "t-";

    /** The whole first label of a draft-edit hostname, and nothing looser. */
    private static final Pattern DRAFT_TOKEN_LABEL = Pattern.compile("^t-[0-9a-f]{32}$");

    /** No grant, no expiry to re-check, and no codes to adopt. */
    private static final Tuple4<Boolean, String, String, String> DRAFT_TOKEN_DENIED = Tuples.of(Boolean.FALSE, "0", "",
            "");

    private final DraftTokenDAO draftTokenDAO;

    /**
     * Long enough that nobody's canvas dies mid-edit, short enough that a leaked
     * hostname is worth little. The heartbeat keeps pushing it while the editor is
     * open, so this is really "how long after the editor closes".
     */
    @Value("${security.draftToken.expiryMinutes:30}")
    private int draftTokenExpiryMinutes;

    public ClientUrlService(CacheService cacheService, SecurityMessageResourceService msgService,
            ClientService clientService, AppService appService, DraftTokenDAO draftTokenDAO) {

        this.cacheService = cacheService;
        this.msgService = msgService;
        this.clientService = clientService;
        this.appService = appService;
        this.draftTokenDAO = draftTokenDAO;
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

    /**
     * Refuse a pattern that is an application's own platform hostname.
     *
     * {@code <appCode>} plus any of {@code security.subdomain.endings} is what
     * {@link ClientService#subdomainAppCode(String)} already serves, for the app's
     * owning client, with no row at all. A CLIENT_URL row is matched BEFORE that
     * fallback, so a row on such a hostname takes an app's default host away from
     * it silently and for good: leadzump.dev.modlix.com served cxapp from February
     * 2025, and the app it names had to be reached on leadzumptest.dev.modlix.com
     * instead. adzump and sitezump went the same way the same week.
     *
     * Refused even when the row names that same app. The fallback already answers
     * on that hostname, so the row can only ever change which client is served
     * there, and having two mechanisms disagreeing about one hostname is worth
     * less than the confusion it buys.
     *
     * Only the hostname is looked at, parsed with the resolver's own regex: a
     * pattern may carry a scheme and a port, and neither has any bearing on this.
     */
    private Mono<Boolean> checkNotAnAppSubdomain(String urlPattern) {

        String host = ClientUrlPattern.hostOf(urlPattern);
        String appCode = this.clientService.subdomainAppCode(host);

        if (appCode == null)
            return Mono.just(Boolean.TRUE);

        return this.appService.getAppByCode(appCode)
                .flatMap(app -> this.msgService.<Boolean>throwMessage(
                        msg -> new GenericException(HttpStatus.CONFLICT, msg),
                        SecurityMessageResourceService.URL_IS_APP_SUBDOMAIN, host, app.getAppCode()))
                .defaultIfEmpty(Boolean.TRUE);
    }

    @PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
    @Override
    public Mono<ClientUrl> create(ClientUrl entity) {

        entity.setUrlPattern(trimBackSlash(entity.getUrlPattern()));

        return this.checkNotAnAppSubdomain(entity.getUrlPattern()).flatMap(ok -> FlatMapUtil.flatMapMono(

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
                })).contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientUrlService.create"))
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

        // Guarded as tightly as create: an existing row edited onto an app's own
        // hostname hijacks it exactly as a new one would.
        return this.checkNotAnAppSubdomain(entity.getUrlPattern())
                .flatMap(ok -> super.update(entity))
                .flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URL))
                .flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URI))
                .flatMap(cacheService.evictAllFunction(CACHE_NAME_GATEWAY_URL_CLIENT_APP_CODE))
                .flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE))
                .flatMap(cacheService.evictAllFunction(SSLCertificateService.CACHE_NAME_CERTIFICATE_LAST_UPDATED_AT));
    }

    @PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
    @Override
    public Mono<ClientUrl> update(ULong key, Map<String, Object> updateFields) {

        updateFields.computeIfPresent(URL_PATTERN, (k, v) -> trimBackSlash(v.toString()));

        // A patch that does not touch urlPattern cannot move the row onto a
        // hostname, so it is not worth a lookup.
        Object patched = updateFields.get(URL_PATTERN);

        return (patched == null ? Mono.just(Boolean.TRUE) : this.checkNotAnAppSubdomain(patched.toString()))
                .flatMap(ok -> super.update(key, updateFields))
                .flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URL))
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

    /**
     * Whether a registrant may claim this subdomain. TRUE means available.
     *
     * Two ways it is not: somebody already holds the hostname, or the label is an
     * existing application's code, in which case the hostname is that app's own
     * platform host and handing it to a client would take it away from the app.
     *
     * The second branch used to run only when the hostname was ALREADY taken, and
     * to answer TRUE there -- so a colliding appCode reported the subdomain as
     * available, and the check that was meant to reserve app codes instead waved
     * duplicates through. Nothing had noticed because the app that matters,
     * `leadzump`, was created two months AFTER the row that took its hostname, so
     * the branch never ran on the case it existed for.
     */
    public Mono<Boolean> checkSubDomainAvailability(String subDomain, String fullURL) {

        return FlatMapUtil.flatMapMono(

                () -> this.checkSubDomainAvailabilityWithSuffix(fullURL),

                free -> {
                    if (!BooleanUtil.safeValueOf(free))
                        return Mono.just(Boolean.FALSE);

                    return this.appService.getAppByCode(subDomain).map(e -> Boolean.FALSE)
                            .defaultIfEmpty(Boolean.TRUE);
                }).contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientUrlService.checkSubDomainAvailability"));
    }

    public Mono<ClientUrl> createForRegistration(ClientUrl entity) {

        entity.setUrlPattern(trimBackSlash(entity.getUrlPattern()));

        // Registration pre-flights the label through checkSubDomainAvailability
        // above, but only for a business client and only when a subDomain was
        // supplied. This is the same guard on the write itself, so no path into
        // this table can mint an app's platform hostname.
        return this.checkNotAnAppSubdomain(entity.getUrlPattern())
                .flatMap(ok -> super.create(entity))
                .flatMap(cacheService.evictAllFunction(CACHE_NAME_CLIENT_URL))
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

    /**
     * An app's LIVE URLs. A blank clientCode means every client's, not none.
     *
     * The workspace pane lists an app's addresses across clients and uses its
     * client picker to narrow them, so "all of them" had to become expressible.
     * The two cases are authorized differently and deliberately so:
     *
     * - a named client still needs {@code isUserClientManageClient}, unchanged,
     *   so asking for one client's URLs proves you manage that client;
     * - the unnamed case cannot ask that question, so the DAO is handed the
     *   caller's own client id and scopes the rows to the hierarchy it manages.
     *   Null goes down only for a SYSTEM caller. Read access to the app alone
     *   must not reveal every client's hostnames.
     */
    public Mono<List<ClientUrl>> getClientUrls(String appCode, String clientCode) {

        boolean allClients = StringUtil.safeIsBlank(clientCode);

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.appService.hasReadAccess(appCode, ca.getClientCode()).filter(BooleanUtil::safeValueOf),

                (ca, hasAccess) -> allClients ? Mono.just(Boolean.TRUE)
                        : this.clientService.isUserClientManageClient(ca, clientCode)
                                .filter(BooleanUtil::safeValueOf),

                (ca, hasAccess, hasClientAccess) -> this.dao.getClientUrls(appCode, clientCode,
                        allClients && !ca.isSystemClient() ? ULongUtil.valueOf(ca.getUser().getClientId()) : null))
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
        label.append(randomHex(raw));

        return label + this.appCodeSuffix + DRAFT_HOST_BASE_DOMAIN;
    }

    /** 128 bits of {@link #DRAFT_HOST_RANDOM} as 32 lowercase hex characters. */
    private static String randomHex(byte[] raw) {

        StringBuilder hex = new StringBuilder(raw.length * 2);
        for (byte b : raw)
            hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        return hex.toString();
    }

    // ── Draft edit token ───────────────────────────────────────────
    //
    // The permanent draft link above is per (client, app) and anonymous. This is
    // the other half: a grant that lasts one editing session, so the page editor
    // can point its preview iframes at the draft surface for whichever client the
    // person is previewing, which the permanent link cannot express because it is
    // minted against the logged-in client only.
    //
    // It is carried as a hostname for one reason: the surface has to cover the
    // iframe's own document, the <link> to api/ui/style, EventSource and every
    // nested navigation inside the preview, none of which can carry a header.

    /**
     * The editing grant for one app: the caller's live one if they already hold it,
     * a new one otherwise.
     *
     * Gated exactly like {@link #mintDraftUrl(String)}, and for the same reason:
     * this hands out unpublished work, so app write access is the bar, not the
     * broader Authorities.Client_UPDATE the rest of this service uses. The gate runs
     * before the reuse lookup, so someone whose write access was removed cannot get
     * their old grant handed back to them.
     *
     * Reuse, and not rotation, is what a repeat call gets. Rotation is the wrong
     * answer twice over: the token IS a hostname, so a second window opening on the
     * same app would sit on a different origin from the first, and the gateway would
     * have to resolve and cache that second host from cold. Handing back the one
     * grant means a refresh, a second window and a restored tab all land on the
     * origin already resolved, and the row count stops tracking how many times
     * anybody opened the editor.
     *
     * Note what this deliberately does not do: it never revokes. The loser of a
     * simultaneous open keeps its own grant until that grant's own expiry, because
     * pulling a token out from under an open editor would change the origin its
     * iframes are already on. Grants only ever die by expiring.
     */
    public Mono<DraftTokenResponse> mintDraftToken(String appCode) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> ca.isSystemClient() ? Mono.just(Boolean.TRUE)
                        : this.appService.hasWriteAccess(appCode, ca.getClientCode()),

                (ca, hasAccess) -> BooleanUtil.safeValueOf(hasAccess)
                        ? this.clientService.getClientBy(ca.getClientCode())
                        : Mono.empty(),

                (ca, hasAccess, client) -> this.reuseOrCreateDraftToken(appCode, client.getId(),
                        ULongUtil.valueOf(ca.getUser().getId())),

                (ca, hasAccess, client, token) -> Mono.just(new DraftTokenResponse()
                        .setToken(token.getToken())
                        .setHost(draftTokenHost(token.getToken()))
                        .setExpiresAt(token.getExpiresAt())))

                .switchIfEmpty(this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_WRITE_APPLICATION_ACCESS))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientUrlService.mintDraftToken"));
    }

    /**
     * The caller's live grant for this app, pushed forward, or a brand new one.
     *
     * A reused grant always leaves here with a full window ahead of it, so the
     * caller never has to care which branch it came from -- a row with four minutes
     * left is worth reusing precisely because this is also the moment it gets
     * extended. That extension cannot miss: the read filters out expired rows, and
     * the only thing that deletes rows is the cleanup job, which takes expired ones
     * only. The update's affected-row count is therefore not worth branching on,
     * which is just as well -- MySQL reports rows CHANGED, not matched, so two opens
     * inside the same second would report zero for a no-op write of the same
     * second-resolution timestamp.
     */
    private Mono<DraftToken> reuseOrCreateDraftToken(String appCode, ULong clientId, ULong userId) {

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime expiresAt = now.plusMinutes(this.draftTokenExpiryMinutes);

        return this.draftTokenDAO.readLiveOfUser(userId, appCode, clientId, now)

                .flatMap(existing -> this.draftTokenDAO.extend(existing.getToken(), userId, expiresAt)
                        .thenReturn(existing.setExpiresAt(expiresAt)))

                .switchIfEmpty(Mono.defer(() -> {

                    byte[] raw = new byte[DRAFT_HOST_RANDOM_BYTES];
                    DRAFT_HOST_RANDOM.nextBytes(raw);

                    DraftToken token = new DraftToken();
                    token.setToken(randomHex(raw))
                            .setAppCode(appCode)
                            .setClientId(clientId)
                            .setUserId(userId)
                            .setExpiresAt(expiresAt);

                    return this.draftTokenDAO.create(token);
                }));
    }

    /**
     * Push an open editor's grant forward, keeping the same token.
     *
     * Never mints a replacement. The token IS the hostname, so a new value would
     * change the iframes' origin and reload all three canvases, losing scroll
     * position and everything the previewed page holds in its own store. The
     * property that matters is that the grant dies shortly after the last editor
     * holding it is closed, and expiry alone gives that.
     *
     * Since mintDraftToken hands the same grant to every window of the same user on
     * the same app, several editors can be beating on one token at once. That is
     * harmless: each beat is the same idempotent write of an absolute expiry, so the
     * grant simply lives as long as the longest-lived of them.
     *
     * Nothing is evicted here, and that is not an omission. The gateway caches what
     * it resolved, but its cache cannot be cleared from this side: CacheService
     * scopes a cache name by the service's own redis.cache.prefix, so an evictAll
     * here would clear "sec-gatewayDraftToken", which nothing writes. The gateway
     * instead stamps each cached answer with how long it may be trusted, capped at a
     * minute, so an extension is picked up within a minute of this call.
     */
    public Mono<DraftTokenResponse> extendDraftToken(String token) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.draftTokenDAO.readByToken(token),

                (ca, existing) -> {

                    // Scoped to the minting user. A heartbeat is not a way to keep
                    // somebody else's grant alive.
                    if (!ULongUtil.valueOf(ca.getUser().getId()).equals(existing.getUserId()))
                        return Mono.empty();

                    LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC)
                            .plusMinutes(this.draftTokenExpiryMinutes);

                    return this.draftTokenDAO.extend(token, existing.getUserId(), expiresAt)
                            .map(count -> existing.setExpiresAt(expiresAt));
                },

                (ca, existing, updated) -> Mono.just(new DraftTokenResponse()
                        .setToken(updated.getToken())
                        .setHost(draftTokenHost(updated.getToken()))
                        .setExpiresAt(updated.getExpiresAt())))

                .switchIfEmpty(this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_WRITE_APPLICATION_ACCESS))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientUrlService.extendDraftToken"));
    }

    /**
     * Whether a hostname's draft-edit token grants the draft surface, and for which
     * app and client.
     *
     * {@code (allowed, expiresAtEpochSeconds, appCode, clientCode)}.
     *
     * Called by the gateway on every request whose first host label looks like a
     * token, so it answers rather than throws: an unparseable, unknown, mismatched
     * or expired token is a plain false and the request is served live.
     *
     * The expiry comes back because the gateway caches this and {@code CacheService}
     * has no per-entry TTL -- its Caffeine backstop is an hour, twice a token's
     * life, so the gateway re-checks the timestamp itself and a stale entry cannot
     * authorise anything. It is a STRING rather than a number on purpose: the tuple
     * deserializer reads elements as plain Objects, so epoch seconds would arrive as
     * an Integer and the declared Long would blow up on the cast.
     *
     * Blank supplied codes mean the caller had none to offer -- a request straight
     * to the token hostname with no {@code /appCode/clientCode} path prefix -- and
     * the token's own codes are returned for the gateway to adopt.
     */
    public Mono<Tuple4<Boolean, String, String, String>> resolveDraftToken(String host, String appCode,
            String clientCode) {

        String token = draftTokenFromHost(host);

        if (token == null)
            return Mono.just(DRAFT_TOKEN_DENIED);

        return this.draftTokenDAO.readByToken(token)
                .flatMap(row -> {

                    if (row.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC)))
                        return Mono.just(DRAFT_TOKEN_DENIED);

                    if (!StringUtil.safeIsBlank(appCode) && !row.getAppCode().equalsIgnoreCase(appCode))
                        return Mono.just(DRAFT_TOKEN_DENIED);

                    String expiry = String.valueOf(row.getExpiresAt().toEpochSecond(ZoneOffset.UTC));

                    return this.clientService.readInternal(row.getClientId())
                            .flatMap(minting -> StringUtil.safeIsBlank(clientCode)
                                    ? Mono.just(Tuples.of(Boolean.TRUE, expiry, row.getAppCode(), minting.getCode()))

                                    // The client being previewed must be the minting
                                    // client or one it manages. isClientBeingManagedBy
                                    // short-circuits on equality, so the same-client
                                    // case costs no hierarchy read -- do not "simplify"
                                    // that away, it is the common case.
                                    : this.clientService.getClientBy(clientCode)
                                            .flatMap(requested -> this.clientService
                                                    .doesClientManageClient(row.getClientId(), requested.getId()))
                                            .map(manages -> BooleanUtil.safeValueOf(manages)
                                                    ? Tuples.of(Boolean.TRUE, expiry, row.getAppCode(), clientCode)
                                                    : DRAFT_TOKEN_DENIED))
                            .defaultIfEmpty(DRAFT_TOKEN_DENIED);
                })
                .defaultIfEmpty(DRAFT_TOKEN_DENIED)
                .onErrorReturn(DRAFT_TOKEN_DENIED)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientUrlService.resolveDraftToken"));
    }

    /**
     * Drop draft-edit tokens whose expiry has passed.
     *
     * Rows now accumulate at roughly the rate of (person, app) pairs that go a whole
     * expiry window without being edited, rather than at the rate people open the
     * page editor, because minting reuses the caller's live grant. Nothing else
     * removes them. An expired row grants nothing either way; this is housekeeping,
     * not enforcement.
     */
    public Mono<Integer> cleanupExpiredDraftTokens() {
        return this.draftTokenDAO.deleteExpired();
    }

    /** {@code t-<32 hex><appCodeSuffix>.modlix.com}, the sibling of {@link #newDraftHost()}. */
    private String draftTokenHost(String token) {
        return DRAFT_TOKEN_HOST_PREFIX + token + this.appCodeSuffix + DRAFT_HOST_BASE_DOMAIN;
    }

    /**
     * The token inside a hostname, or null when the first label is not one.
     *
     * Matched against the first label only, so an ordinary app hostname that merely
     * begins with a "t" cannot be mistaken for a token, and the environment suffix
     * never has to be parsed back out.
     */
    static String draftTokenFromHost(String host) {

        if (StringUtil.safeIsBlank(host))
            return null;

        int dot = host.indexOf('.');
        String label = dot == -1 ? host : host.substring(0, dot);

        return DRAFT_TOKEN_LABEL.matcher(label).matches()
                ? label.substring(DRAFT_TOKEN_HOST_PREFIX.length())
                : null;
    }
}
