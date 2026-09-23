package com.fincity.saas.ui.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.ui.document.Application;
import com.google.gson.Gson;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Proxies dashboard reads to the analytics engine.
 *
 * The engine answers a closed set of widgets and has no query language, so there is nothing
 * here to sanitise and no expression to rewrite — which is why the HogQL rewriter this
 * service used to depend on is gone rather than ported. Two things are still this side's job,
 * and both are things the engine cannot know:
 *
 * <ul>
 * <li><b>Who may read a site.</b> The engine holds one shared secret and trusts whoever
 * presents it. Authorisation is Modlix's vocabulary — write access to the application, plus
 * the caller's own client managing the target client — so it is enforced here, before the
 * request is made.</li>
 * <li><b>Which day boundary the numbers use.</b> Rollups are hourly and timezone-free; the
 * zone only selects which buckets are summed. The order is the zone on the request, then the
 * client's own {@code security_client.TIME_ZONE}, then Asia/Kolkata.</li>
 * </ul>
 *
 * The site is always computed here and never read from the request body. A caller who could
 * name their own site could read another tenant's numbers, and no amount of checking
 * afterwards would undo that.
 */
@Service
public class AnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsService.class);

    public static final String CACHE_NAME_ANALYTICS_QUERY = "analyticsQueryCache";

    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * The last resort in the timezone chain. Not UTC: this platform's clients are
     * overwhelmingly in one place, and the same default is already what
     * {@code security_client.TIME_ZONE} carries, so the two cannot disagree.
     */
    private static final String FALLBACK_TIME_ZONE = "Asia/Kolkata";

    private static final String KEY_SITE = "site";
    private static final String KEY_TIMEZONE = "timezone";

    @Value("${ui.analytics.engine.url:}")
    private String engineUrl;

    @Value("${ui.analytics.engine.secret:}")
    private String engineSecret;

    private final ApplicationService appService;
    private final CacheService cacheService;
    private final WebClient.Builder webClientBuilder;
    private final UIMessageResourceService messageResourceService;
    private final FeignAuthenticationService securityService;
    private final Gson gson = new Gson();

    private WebClient engineClient;

    public AnalyticsService(ApplicationService appService, CacheService cacheService,
            WebClient.Builder webClientBuilder, UIMessageResourceService messageResourceService,
            FeignAuthenticationService securityService) {
        this.appService = appService;
        this.cacheService = cacheService;
        this.webClientBuilder = webClientBuilder;
        this.messageResourceService = messageResourceService;
        this.securityService = securityService;
    }

    @PostConstruct
    void initialize() {
        if (StringUtil.safeIsBlank(engineUrl) || StringUtil.safeIsBlank(engineSecret)) {
            logger.warn("Analytics query proxy disabled — ui.analytics.engine.url / .secret are not set");
            return;
        }
        this.engineClient = webClientBuilder.baseUrl(engineUrl).build();
    }

    public Mono<Map<String, Object>> query(String appCode, String clientCode, Map<String, Object> requestBody) {

        return FlatMapUtil.flatMapMono(

                () -> checkConfiguredAndAuthorized(appCode, clientCode),

                ca -> resolveTimeZone(clientCode, requestBody),

                (ca, zone) -> {
                    Map<String, Object> request = engineRequest(requestBody, appCode, clientCode, zone);
                    String cacheKey = hash(this.gson.toJson(request));
                    String userName = ca.getUser() == null ? "anonymous" : ca.getUser().getUserName();
                    return this.cacheService.<Map<String, Object>>cacheValueOrGet(CACHE_NAME_ANALYTICS_QUERY,
                            () -> executeRemote(request, appCode, clientCode, userName), cacheKey);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AnalyticsService.query"));
    }

    /**
     * Builds what the engine is actually asked, from what the caller asked for.
     *
     * Everything the caller sent survives except {@code site}, which is overwritten rather
     * than validated: overwriting cannot be got wrong, where validating can.
     */
    private Map<String, Object> engineRequest(Map<String, Object> requestBody, String appCode, String clientCode,
            String zone) {

        Map<String, Object> request = requestBody == null ? new LinkedHashMap<>() : new LinkedHashMap<>(requestBody);
        request.put(KEY_SITE, siteKey(appCode, clientCode));
        request.put(KEY_TIMEZONE, zone);
        return request;
    }

    /**
     * The storage identity of a site, and it must match the engine's own spelling exactly:
     * appCode lower case, clientCode upper. The platform is inconsistent about case — the
     * gateway compares these case-insensitively and a URL can carry either — so both ends
     * canonicalise rather than hoping.
     */
    static String siteKey(String appCode, String clientCode) {
        return appCode.toLowerCase() + "_" + clientCode.toUpperCase();
    }

    /**
     * Request, then the client's own zone, then Asia/Kolkata.
     *
     * An unusable zone on the request is a 400 rather than a silent fallback: a dashboard
     * quietly answering in a different zone from the one it was asked for is a number nobody
     * can reconcile. An unusable zone on the CLIENT record is different — nobody asked for it
     * in this request and refusing would take the dashboard down for a bad row — so that one
     * falls through with a warning.
     */
    private Mono<String> resolveTimeZone(String clientCode, Map<String, Object> requestBody) {

        Object requested = requestBody == null ? null : requestBody.get(KEY_TIMEZONE);
        if (requested != null && !StringUtil.safeIsBlank(requested.toString())) {
            String zone = requested.toString().trim();
            if (!isValidZone(zone))
                return this.messageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        UIMessageResourceService.ANALYTICS_UNKNOWN_TIMEZONE, zone);
            return Mono.just(zone);
        }

        return this.securityService.getClientByCode(clientCode)
                .map(client -> {
                    String zone = client == null ? null : client.getTimeZone();
                    if (StringUtil.safeIsBlank(zone))
                        return FALLBACK_TIME_ZONE;
                    if (!isValidZone(zone)) {
                        logger.warn("analytics_timezone clientCode={} has an unusable zone '{}', using {}",
                                clientCode, zone, FALLBACK_TIME_ZONE);
                        return FALLBACK_TIME_ZONE;
                    }
                    return zone;
                })
                .defaultIfEmpty(FALLBACK_TIME_ZONE);
    }

    private static boolean isValidZone(String zone) {
        try {
            ZoneId.of(zone);
            return true;
        } catch (DateTimeException e) {
            return false;
        }
    }

    private Mono<ContextAuthentication> checkConfiguredAndAuthorized(String appCode, String clientCode) {

        if (engineClient == null)
            return this.messageResourceService.throwMessage(
                    msg -> new GenericException(HttpStatus.SERVICE_UNAVAILABLE, msg),
                    UIMessageResourceService.ANALYTICS_NOT_CONFIGURED);

        if (StringUtil.safeIsBlank(appCode) || StringUtil.safeIsBlank(clientCode))
            return this.messageResourceService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    UIMessageResourceService.ANALYTICS_CODES_REQUIRED);

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.securityService.hasWriteAccess(appCode, clientCode)
                        .filter(BooleanUtil::safeValueOf)
                        .switchIfEmpty(this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                UIMessageResourceService.ANALYTICS_NO_WRITE_ACCESS)),

                (ca, hasWrite) -> this.securityService
                        .doesClientManageClientCode(ca.getClientCode(), clientCode)
                        .filter(BooleanUtil::safeValueOf)
                        .switchIfEmpty(this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                UIMessageResourceService.ANALYTICS_CLIENT_NOT_MANAGED)),

                (ca, hasWrite, isManaged) -> this.appService.read(appCode, appCode, clientCode)
                        .flatMap(wrapper -> {
                            Application app = wrapper == null ? null : wrapper.getObject();
                            if (app == null || !isAnalyticsEnabled(app))
                                return this.messageResourceService.throwMessage(
                                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                        UIMessageResourceService.ANALYTICS_NOT_ENABLED);
                            return Mono.just(ca);
                        }));
    }

    @SuppressWarnings("unchecked")
    private Mono<Map<String, Object>> executeRemote(Map<String, Object> body, String appCode, String clientCode,
            String userName) {

        long startedAt = System.currentTimeMillis();

        return this.engineClient.post()
                .uri("/q")
                .header(HEADER_AUTHORIZATION, BEARER_PREFIX + engineSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(m -> (Map<String, Object>) m)
                .doOnSuccess(resp -> logger.info(
                        "analytics_query appCode={} clientCode={} userName={} widget={} durationMs={} ok=true",
                        appCode, clientCode, userName, body.get("widget"), System.currentTimeMillis() - startedAt))
                .doOnError(err -> logger.warn(
                        "analytics_query appCode={} clientCode={} userName={} widget={} durationMs={} ok=false error={}",
                        appCode, clientCode, userName, body.get("widget"), System.currentTimeMillis() - startedAt,
                        err.getMessage()));
    }

    @SuppressWarnings("unchecked")
    private static boolean isAnalyticsEnabled(Application app) {
        Map<String, Object> props = app.getProperties();
        if (props == null) return false;
        Object analytics = props.get("analytics");
        if (!(analytics instanceof Map)) return false;
        return Boolean.TRUE.equals(((Map<String, Object>) analytics).get("enabled"));
    }

    private static String hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
