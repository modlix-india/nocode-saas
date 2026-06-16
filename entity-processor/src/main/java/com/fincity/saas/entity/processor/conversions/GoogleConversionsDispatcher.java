package com.fincity.saas.entity.processor.conversions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.dto.ConversionActionMapping;
import com.fincity.saas.entity.processor.dto.ConversionEvent;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import com.fincity.saas.entity.processor.service.commons.AbstractConnectionService;
import com.fincity.saas.entity.processor.util.ConversionsApiHashUtil;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Google Ads UploadClickConversions dispatcher. Uses the Google Ads REST API
 * with enhanced-conversions user_identifiers (SHA-256 hashed email + phone).
 *
 * <p>API: POST {@code googleads.googleapis.com/v23/customers/{customerId}:uploadClickConversions}.
 *
 * <p>Click attribution priority on a single conversion: gclid → wbraid → gbraid.
 * Without any click identifier we still attempt enhanced-conversions match via
 * hashed user_identifiers, which Google Ads will attribute to a click within
 * the lookback window.
 */
@Service
public class GoogleConversionsDispatcher extends AbstractConversionsDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(GoogleConversionsDispatcher.class);

    private static final String SCHEME = "https";
    private static final String HOST = "googleads.googleapis.com";
    private static final String API_VERSION = "/v23/";
    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssxxx");

    // Default body buffer in Spring WebClient is 256 KB which Google Ads insights
    // responses (yearly daily-segmented data) blow past. Bump to 16 MB so large
    // GAQL responses don't fail with DataBufferLimitException.
    private static final WebClient webClient = WebClient.builder()
            .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();

    @Value("${ai.adzump.googleAds.developerToken}")
    private String googleDeveloperToken;

    public GoogleConversionsDispatcher(AbstractConnectionService connectionService) {
        // connectionService no longer needed here — developer-token comes from config,
        // OAuth access token is resolved by the drain service and passed in.
    }

    @Override
    public CampaignPlatform getPlatform() {
        return CampaignPlatform.GOOGLE;
    }

    @Override
    public Mono<DispatchResult> dispatch(
            ConversionEvent event,
            ConversionActionMapping mapping,
            Ticket ticket,
            Campaign campaign,
            String accessToken) {

        String customerId = campaign.getPlatformAccountId();
        String loginCustomerId = campaign.getPlatformLoginId();
        if (customerId == null || customerId.isBlank()) {
            return Mono.just(DispatchResult.fail(
                    "Google requires customerId (campaign.platformAccountId); was null", null));
        }
        if (this.googleDeveloperToken == null || this.googleDeveloperToken.isBlank()) {
            return Mono.just(DispatchResult.fail(
                    "ai.adzump.googleAds.developerToken is not configured", null));
        }

        return postClickConversion(
                        event, mapping, ticket, accessToken, customerId, loginCustomerId, this.googleDeveloperToken)
                .onErrorResume(t -> Mono.just(DispatchResult.fail("Google dispatch failed: " + t.getMessage(), null)));
    }

    private Mono<DispatchResult> postClickConversion(
            ConversionEvent event,
            ConversionActionMapping mapping,
            Ticket ticket,
            String accessToken,
            String customerId,
            String loginCustomerId,
            String developerToken) {

        Map<String, Object> conversion = buildConversion(event, mapping, ticket);
        Map<String, Object> payload = Map.of(
                "conversions", List.of(conversion),
                "partial_failure", Boolean.TRUE,
                "validate_only", Boolean.FALSE);

        return webClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .scheme(SCHEME)
                        .host(HOST)
                        .path(API_VERSION + "customers/" + customerId + ":uploadClickConversions")
                        .build())
                .headers(h -> {
                    h.setBearerAuth(accessToken);
                    h.add("developer-token", developerToken);
                    if (loginCustomerId != null && !loginCustomerId.isBlank()) {
                        h.add("login-customer-id", loginCustomerId);
                    }
                })
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(body -> {
                    Map<String, Object> response = jsonNodeToMap(body);
                    JsonNode partialFailure = body.path("partialFailureError");
                    if (!partialFailure.isMissingNode() && !partialFailure.isNull()) {
                        return DispatchResult.fail(
                                "Google partial-failure: " + partialFailure.toString(), response);
                    }
                    int results = body.path("results").size();
                    if (results <= 0) {
                        return DispatchResult.fail(
                                "Google returned 0 results: " + body.toString(), response);
                    }
                    return DispatchResult.ok("Google accepted " + results + " conversion(s)", response);
                })
                .onErrorResume(WebClientResponseException.class, e -> {
                    logger.warn("Google CAPI error for event {}: {} {}", event.getEventId(), e.getStatusCode(), e.getResponseBodyAsString());
                    Map<String, Object> body = new HashMap<>();
                    body.put("status", e.getStatusCode().value());
                    body.put("body", e.getResponseBodyAsString());
                    return Mono.just(DispatchResult.fail("Google " + e.getStatusCode() + ": " + e.getMessage(), body));
                })
                .onErrorResume(t -> Mono.just(DispatchResult.fail("Google dispatch failed: " + t.getMessage(), null)));
    }

    private Map<String, Object> buildConversion(ConversionEvent event, ConversionActionMapping mapping, Ticket ticket) {

        Map<String, Object> conversion = new HashMap<>();
        // platform_action_id is a fully-qualified resource name, e.g.
        // "customers/1234/conversionActions/567"
        conversion.put("conversion_action", mapping.getPlatformActionId());
        conversion.put("conversion_date_time", conversionDateTime(event.getCreatedAt()));
        conversion.put("order_id", event.getEventId());
        if (mapping.getDefaultValue() != null) conversion.put("conversion_value", mapping.getDefaultValue());
        if (mapping.getCurrency() != null) conversion.put("currency_code", mapping.getCurrency());

        Map<String, Object> adData = ticket.getAdData();
        if (adData != null) {
            Object gclid = adData.get("gclid");
            Object wbraid = adData.get("wbraid");
            Object gbraid = adData.get("gbraid");
            if (gclid != null) conversion.put("gclid", gclid);
            else if (wbraid != null) conversion.put("wbraid", wbraid);
            else if (gbraid != null) conversion.put("gbraid", gbraid);
        }

        List<Map<String, Object>> userIdentifiers = new java.util.ArrayList<>();
        if (ticket.getEmail() != null && !ticket.getEmail().isBlank()) {
            userIdentifiers.add(Map.of("hashed_email", ConversionsApiHashUtil.hashEmail(ticket.getEmail())));
        }
        if (ticket.getPhoneNumber() != null && !ticket.getPhoneNumber().isBlank()) {
            String fullPhone = (ticket.getDialCode() == null ? "" : ticket.getDialCode().toString()) + ticket.getPhoneNumber();
            userIdentifiers.add(Map.of("hashed_phone_number", ConversionsApiHashUtil.hashPhone(fullPhone)));
        }
        if (!userIdentifiers.isEmpty()) conversion.put("user_identifiers", userIdentifiers);

        return conversion;
    }

    /**
     * Google attributes on conversion_date_time within the click lookback window,
     * so it must be the actual stage-transition time (stored UTC on the outbox
     * row), not the deferred worker dispatch time. Falls back to now if absent.
     */
    private static String conversionDateTime(LocalDateTime createdAt) {
        OffsetDateTime odt = createdAt != null ? createdAt.atOffset(ZoneOffset.UTC) : OffsetDateTime.now(ZoneOffset.UTC);
        return odt.format(TS_FORMAT);
    }

    private static Map<String, Object> jsonNodeToMap(JsonNode node) {
        if (node == null || node.isNull()) return Map.of();
        Map<String, Object> out = new HashMap<>();
        node.fields().forEachRemaining(e -> {
            JsonNode v = e.getValue();
            if (v.isInt()) out.put(e.getKey(), v.intValue());
            else if (v.isLong()) out.put(e.getKey(), v.longValue());
            else if (v.isBoolean()) out.put(e.getKey(), v.booleanValue());
            else if (v.isTextual()) out.put(e.getKey(), v.textValue());
            else out.put(e.getKey(), v.toString());
        });
        return out;
    }
}
