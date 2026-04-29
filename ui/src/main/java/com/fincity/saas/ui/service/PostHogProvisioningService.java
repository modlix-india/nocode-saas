package com.fincity.saas.ui.service;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.ui.document.Application;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Provisions a PostHog project per Modlix application.
 *
 * Calls the PostHog admin API (which lives on the analytics VM, reachable only via the
 * private VCN — never through the Cloudflare proxy) to create a project, captures the
 * returned project API token, and stores it on the application's properties.analytics block.
 *
 * Idempotent: if the application already has a projectApiKey, returns the existing
 * analytics config without contacting PostHog.
 *
 * Provisioning leaves {@code enabled: false}; an admin must flip the toggle separately.
 * This prevents an accidental save from starting collection on every visitor.
 */
@Service
public class PostHogProvisioningService {

    private static final Logger logger = LoggerFactory.getLogger(PostHogProvisioningService.class);

    private static final String KEY_ANALYTICS = "analytics";
    private static final String KEY_PROJECT_API_KEY = "projectApiKey";
    private static final String KEY_INGESTION_HOST = "ingestionHost";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_PROVIDER = "provider";

    @Value("${ui.analytics.posthog.adminBaseUrl:}")
    private String adminBaseUrl;

    @Value("${ui.analytics.posthog.adminToken:}")
    private String adminToken;

    @Value("${ui.analytics.posthog.organizationId:}")
    private String organizationId;

    @Value("${ui.analytics.allowedIngestionHost:}")
    private String allowedIngestionHost;

    private final ApplicationService applicationService;
    private final UIMessageResourceService messageResourceService;
    private final WebClient.Builder webClientBuilder;

    public PostHogProvisioningService(ApplicationService applicationService,
                                      UIMessageResourceService messageResourceService,
                                      WebClient.Builder webClientBuilder) {
        this.applicationService = applicationService;
        this.messageResourceService = messageResourceService;
        this.webClientBuilder = webClientBuilder;
    }

    @PreAuthorize("hasAuthority('Authorities.Application_UPDATE')")
    public Mono<Map<String, Object>> provisionForApp(String appCode) {

        if (StringUtil.safeIsBlank(adminBaseUrl) || StringUtil.safeIsBlank(adminToken)
                || StringUtil.safeIsBlank(organizationId)) {
            return this.messageResourceService.<Map<String, Object>>throwMessage(
                    msg -> new GenericException(HttpStatus.SERVICE_UNAVAILABLE, msg),
                    UIMessageResourceService.ANALYTICS_FIELD_REQUIRED, "posthog admin config");
        }

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> applicationService.read(appCode, appCode, ca.getUrlClientCode())
                        .map(owid -> new Application(owid.getObject())),

                (ca, app) -> {
                    Map<String, Object> existing = readAnalytics(app);
                    if (!StringUtil.safeIsBlank(
                            StringUtil.safeValueOf(existing.get(KEY_PROJECT_API_KEY), "")))
                        return Mono.just(existing);

                    return createPostHogProject(app)
                            .flatMap(projectToken -> persistProvisioning(app, projectToken));
                })
                .switchIfEmpty(this.messageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                        UIMessageResourceService.ANALYTICS_FIELD_REQUIRED, appCode))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "PostHogProvisioningService.provisionForApp"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readAnalytics(Application app) {
        if (app.getProperties() == null) return Map.of();
        Object analytics = app.getProperties().get(KEY_ANALYTICS);
        return analytics instanceof Map ? (Map<String, Object>) analytics : Map.of();
    }

    private Mono<String> createPostHogProject(Application app) {

        String projectName = (app.getClientCode() == null ? "" : app.getClientCode() + "-")
                + app.getAppCode();

        String url = adminBaseUrl.replaceAll("/+$", "")
                + "/api/organizations/" + organizationId + "/projects/";

        Map<String, Object> body = Map.of("name", projectName);

        return webClientBuilder.build()
                .post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    Object token = response.get("api_token");
                    if (token == null) {
                        logger.error("PostHog project creation returned no api_token: {}", response);
                        throw new GenericException(HttpStatus.BAD_GATEWAY,
                                "PostHog did not return api_token");
                    }
                    return token.toString();
                })
                .onErrorResume(e -> {
                    if (e instanceof GenericException) return Mono.error(e);
                    logger.error("PostHog provisioning failed for app {}: {}", app.getAppCode(),
                            e.getMessage());
                    return Mono.error(new GenericException(HttpStatus.BAD_GATEWAY,
                            "PostHog provisioning failed: " + e.getMessage()));
                });
    }

    private Mono<Map<String, Object>> persistProvisioning(Application app, String projectApiKey) {

        Map<String, Object> props = app.getProperties();
        if (props == null) {
            props = new HashMap<>();
            app.setProperties(props);
        }

        Object existingAnalytics = props.get(KEY_ANALYTICS);
        Map<String, Object> analytics;
        if (existingAnalytics instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> casted = (Map<String, Object>) existingAnalytics;
            analytics = casted;
        } else {
            analytics = new HashMap<>();
            props.put(KEY_ANALYTICS, analytics);
        }

        analytics.put(KEY_PROVIDER, "posthog");
        analytics.put(KEY_PROJECT_API_KEY, projectApiKey);
        analytics.putIfAbsent(KEY_INGESTION_HOST, allowedIngestionHost);
        analytics.putIfAbsent(KEY_ENABLED, false);
        analytics.putIfAbsent("autocapture", true);
        analytics.putIfAbsent("capturePageviews", true);
        analytics.putIfAbsent("capturePageleaves", true);
        analytics.putIfAbsent("consentRequired", true);

        Map<String, Object> sessionReplay = new HashMap<>();
        sessionReplay.put(KEY_ENABLED, false);
        sessionReplay.put("maskAllInputs", true);
        analytics.putIfAbsent("sessionReplay", sessionReplay);

        Map<String, Object> finalAnalytics = analytics;
        return applicationService.update(app).thenReturn(finalAnalytics);
    }
}
