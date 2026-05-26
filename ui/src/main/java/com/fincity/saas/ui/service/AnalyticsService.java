package com.fincity.saas.ui.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
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
import com.fincity.saas.ui.util.HogQLTenantRewriter;
import com.google.gson.Gson;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class AnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsService.class);

    public static final String CACHE_NAME_ANALYTICS_QUERY = "analyticsQueryCache";

    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Value("${ui.analytics.ingestionHost:}")
    private String ingestionHost;

    @Value("${ui.analytics.posthog.personalApiKey:}")
    private String personalApiKey;

    @Value("${ui.analytics.posthog.projectId:}")
    private String projectId;

    @Value("${ui.analytics.posthog.playerHost:}")
    private String playerHost;

    private final ApplicationService appService;
    private final CacheService cacheService;
    private final WebClient.Builder webClientBuilder;
    private final UIMessageResourceService messageResourceService;
    private final FeignAuthenticationService securityService;
    private final Gson gson = new Gson();

    private WebClient postHogClient;

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
        if (StringUtil.safeIsBlank(ingestionHost) || StringUtil.safeIsBlank(personalApiKey)
                || StringUtil.safeIsBlank(projectId)) {
            logger.warn("Analytics query proxy disabled — missing personalApiKey / projectId / ingestionHost");
            return;
        }
        this.postHogClient = webClientBuilder.baseUrl(ingestionHost).build();
    }

    public Mono<Map<String, Object>> query(String appCode, String clientCode, Map<String, Object> requestBody) {

        return checkConfiguredAndAuthorized(appCode, clientCode)
                .flatMap(ca -> {
                    Map<String, Object> rewritten = HogQLTenantRewriter.rewrite(requestBody, appCode, clientCode);
                    String cacheKey = hash(appCode + "|" + clientCode + "|" + this.gson.toJson(rewritten));
                    String userName = ca.getUser() == null ? "anonymous" : ca.getUser().getUserName();
                    return this.cacheService.<Map<String, Object>>cacheValueOrGet(CACHE_NAME_ANALYTICS_QUERY,
                            () -> executeRemote(rewritten, appCode, clientCode, userName), cacheKey);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AnalyticsService.query"));
    }

    public Mono<Map<String, Object>> listReplays(String appCode, String clientCode, String dateFrom, String dateTo,
            Integer limit) {

        return checkConfiguredAndAuthorized(appCode, clientCode)
                .flatMap(ca -> {
                    String userName = ca.getUser() == null ? "anonymous" : ca.getUser().getUserName();
                    String propertiesFilter = this.gson.toJson(tenantPropertyFilters(appCode, clientCode));
                    long startedAt = System.currentTimeMillis();
                    return this.postHogClient.get()
                            .uri(uriBuilder -> {
                                uriBuilder.path("/api/projects/{projectId}/session_recordings/")
                                        .queryParam("properties", propertiesFilter);
                                if (!StringUtil.safeIsBlank(dateFrom))
                                    uriBuilder.queryParam("date_from", dateFrom);
                                if (!StringUtil.safeIsBlank(dateTo))
                                    uriBuilder.queryParam("date_to", dateTo);
                                if (limit != null && limit > 0)
                                    uriBuilder.queryParam("limit", limit);
                                return uriBuilder.build(projectId);
                            })
                            .header(HEADER_AUTHORIZATION, BEARER_PREFIX + personalApiKey)
                            .retrieve()
                            .bodyToMono(Map.class)
                            .map(this::asStringObjectMap)
                            .doOnSuccess(resp -> logger.info(
                                    "analytics_replays_list appCode={} clientCode={} userName={} durationMs={} ok=true",
                                    appCode, clientCode, userName, System.currentTimeMillis() - startedAt))
                            .doOnError(err -> logger.warn(
                                    "analytics_replays_list appCode={} clientCode={} userName={} durationMs={} ok=false error={}",
                                    appCode, clientCode, userName, System.currentTimeMillis() - startedAt,
                                    err.getMessage()));
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AnalyticsService.listReplays"));
    }

    public Mono<Map<String, Object>> getReplayPlayback(String appCode, String clientCode, String sessionId) {

        if (StringUtil.safeIsBlank(sessionId))
            return this.messageResourceService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    "sessionId is required");

        return checkConfiguredAndAuthorized(appCode, clientCode)
                .flatMap(ca -> verifyReplayBelongsToTenant(sessionId, appCode, clientCode)
                        .then(createSharingConfig(sessionId))
                        .map(token -> {
                            Map<String, Object> result = new HashMap<>();
                            result.put("sessionId", sessionId);
                            result.put("url", buildEmbedUrl(sessionId, token));
                            return result;
                        }))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AnalyticsService.getReplayPlayback"));
    }

    private Mono<ContextAuthentication> checkConfiguredAndAuthorized(String appCode, String clientCode) {

        if (postHogClient == null)
            return this.messageResourceService.throwMessage(
                    msg -> new GenericException(HttpStatus.SERVICE_UNAVAILABLE, msg),
                    "Analytics service is not configured");

        if (StringUtil.safeIsBlank(appCode) || StringUtil.safeIsBlank(clientCode))
            return this.messageResourceService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    "appCode and clientCode are required");

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.securityService.hasWriteAccess(appCode, clientCode)
                        .filter(BooleanUtil::safeValueOf)
                        .switchIfEmpty(this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                "User does not have write access to this application")),

                (ca, hasWrite) -> this.securityService
                        .doesClientManageClientCode(ca.getClientCode(), clientCode)
                        .filter(BooleanUtil::safeValueOf)
                        .switchIfEmpty(this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                "User's client does not manage the requested client code")),

                (ca, hasWrite, isManaged) -> this.appService.read(appCode, appCode, clientCode)
                        .flatMap(wrapper -> {
                            Application app = wrapper == null ? null : wrapper.getObject();
                            if (app == null || !isAnalyticsEnabled(app))
                                return this.messageResourceService.throwMessage(
                                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                        "Analytics is not enabled for this application");
                            return Mono.just(ca);
                        }));
    }

    @SuppressWarnings("unchecked")
    private Mono<Map<String, Object>> executeRemote(Map<String, Object> body, String appCode, String clientCode,
            String userName) {

        long startedAt = System.currentTimeMillis();

        return this.postHogClient.post()
                .uri("/api/projects/{projectId}/query/", projectId)
                .header(HEADER_AUTHORIZATION, BEARER_PREFIX + personalApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(m -> (Map<String, Object>) m)
                .doOnSuccess(resp -> logger.info(
                        "analytics_query appCode={} clientCode={} userName={} durationMs={} ok=true",
                        appCode, clientCode, userName, System.currentTimeMillis() - startedAt))
                .doOnError(err -> logger.warn(
                        "analytics_query appCode={} clientCode={} userName={} durationMs={} ok=false error={}",
                        appCode, clientCode, userName, System.currentTimeMillis() - startedAt, err.getMessage()));
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> asStringObjectMap(Map<?, ?> raw) {
        return raw == null ? Map.of() : (Map<String, Object>) raw;
    }

    private static List<Map<String, Object>> tenantPropertyFilters(String appCode, String clientCode) {
        Map<String, Object> appFilter = new HashMap<>();
        appFilter.put("key", "app_code");
        appFilter.put("value", appCode);
        appFilter.put("operator", "exact");
        appFilter.put("type", "event");

        Map<String, Object> clientFilter = new HashMap<>();
        clientFilter.put("key", "url_client_code");
        clientFilter.put("value", clientCode);
        clientFilter.put("operator", "exact");
        clientFilter.put("type", "event");

        return List.of(appFilter, clientFilter);
    }

    /**
     * Confirms a session recording belongs to the requesting tenant before
     * we issue a sharing token. Uses HogQL against the events table —
     * cheaper than fetching the full recording object, and bullet-proof:
     * the filter is forced server-side via the rewriter and there's no
     * way for the caller to reach a session that doesn't carry both
     * tenant properties on at least one of its events.
     */
    private Mono<Boolean> verifyReplayBelongsToTenant(String sessionId, String appCode, String clientCode) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("kind", "HogQLQuery");
        envelope.put("query",
                "SELECT count() FROM events WHERE properties.$session_id = '" + sessionId.replace("'", "''")
                        + "' LIMIT 1");

        Map<String, Object> rewritten = HogQLTenantRewriter.rewrite(Map.of("query", envelope), appCode, clientCode);

        return this.postHogClient.post()
                .uri("/api/projects/{projectId}/query/", projectId)
                .header(HEADER_AUTHORIZATION, BEARER_PREFIX + personalApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(rewritten)
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::asStringObjectMap)
                .map(resp -> {
                    Object results = resp.get("results");
                    if (!(results instanceof List<?> rows) || rows.isEmpty()) return false;
                    Object first = rows.get(0);
                    if (!(first instanceof List<?> firstRow) || firstRow.isEmpty()) return false;
                    Object countObj = firstRow.get(0);
                    return countObj instanceof Number n && n.longValue() > 0;
                })
                .flatMap(belongs -> Boolean.TRUE.equals(belongs)
                        ? Mono.just(true)
                        : this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                "Session recording does not belong to this tenant"));
    }

    /**
     * Creates a sharing configuration on the recording (or returns the
     * existing one) and returns the access token that the iframe URL
     * needs.
     */
    private Mono<String> createSharingConfig(String sessionId) {
        return this.postHogClient.post()
                .uri("/api/projects/{projectId}/session_recordings/{sessionId}/sharing/", projectId, sessionId)
                .header(HEADER_AUTHORIZATION, BEARER_PREFIX + personalApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("enabled", true))
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::asStringObjectMap)
                .map(resp -> {
                    Object token = resp.get("access_token");
                    return token == null ? "" : token.toString();
                })
                .flatMap(token -> token.isBlank()
                        ? this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_GATEWAY, msg),
                                "Failed to generate replay sharing token")
                        : Mono.just(token));
    }

    /**
     * Builds the iframe URL the browser will load. Falls back to the
     * ingestion host if no separate playerHost is configured.
     */
    private String buildEmbedUrl(String sessionId, String accessToken) {
        String host = StringUtil.safeIsBlank(playerHost) ? ingestionHost : playerHost;
        if (host.endsWith("/")) host = host.substring(0, host.length() - 1);
        return host + "/embedded/replay/" + sessionId + "?sharing_access_token=" + accessToken;
    }
}
