package com.fincity.saas.entity.processor.conversions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.dto.ConversionActionMapping;
import com.fincity.saas.entity.processor.dto.ConversionEvent;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import com.fincity.saas.entity.processor.enums.ConversionActionSource;
import com.fincity.saas.entity.processor.util.ConversionsApiHashUtil;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Meta Conversions API dispatcher. Implements all 6 critical fixes from the
 * Meta CAPI reference doc Part 6:
 *
 * <ol>
 *   <li>Phone is digit-stripped BEFORE SHA-256 — handled by {@link ConversionsApiHashUtil#hashPhone}.</li>
 *   <li>{@code action_source} is derived from {@link ConversionEvent#getActionSource()} —
 *       {@code website} for browser-form, {@code system_generated} for Meta lead-form.</li>
 *   <li>{@code lead_id} lives in {@code user_data} (NOT custom_data) for lead-form events.</li>
 *   <li>{@code client_ip_address}/{@code client_user_agent} are sent ONLY for {@code website};
 *       omitted for {@code system_generated}.</li>
 *   <li>{@code custom_data} carries only {@code lead_stage} + value/currency from the mapping;
 *       no {@code content_name}/{@code content_category}.</li>
 *   <li>fbc/fbp are read from {@code ticket.adData} and propagate across MQL/SQL events
 *       because the same ticket retains them.</li>
 * </ol>
 */
@Service
public class MetaConversionsDispatcher extends AbstractConversionsDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(MetaConversionsDispatcher.class);

    private static final String SCHEME = "https";
    private static final String HOST = "graph.facebook.com";
    private static final String API_VERSION = "/v24.0/";

    // Default body buffer in Spring WebClient is 256 KB which Google Ads insights
    // responses (yearly daily-segmented data) blow past. Bump to 16 MB so large
    // GAQL responses don't fail with DataBufferLimitException.
    private static final WebClient webClient = WebClient.builder()
            .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();

    @Override
    public CampaignPlatform getPlatform() {
        return CampaignPlatform.FACEBOOK;
    }

    @Override
    public Mono<DispatchResult> dispatch(
            ConversionEvent event,
            ConversionActionMapping mapping,
            Ticket ticket,
            Campaign campaign,
            String accessToken) {

        String datasetId = campaign.getPlatformDatasetId();
        if (datasetId == null || datasetId.isBlank()) {
            return Mono.just(DispatchResult.fail(
                    "Meta CAPI requires platform_dataset_id (pixel id) on the campaign; was null", null));
        }

        Map<String, Object> payload = buildPayload(event, mapping, ticket);

        String testCode = mapping.getTestEventCode();
        if (testCode != null && !testCode.isBlank()) {
            payload.put("test_event_code", testCode);
        }

        return webClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .scheme(SCHEME)
                        .host(HOST)
                        .path(API_VERSION + datasetId + "/events")
                        .queryParam("access_token", accessToken)
                        .build())
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(body -> {
                    Map<String, Object> response = jsonNodeToMap(body);
                    int eventsReceived = body.path("events_received").asInt(-1);
                    if (eventsReceived <= 0) {
                        return DispatchResult.fail(
                                "Meta accepted 0 events: " + body.toString(), response);
                    }
                    return DispatchResult.ok("Meta accepted " + eventsReceived + " event(s)", response);
                })
                .onErrorResume(WebClientResponseException.class, e -> {
                    logger.warn("Meta CAPI error for event {}: {} {}", event.getEventId(), e.getStatusCode(), e.getResponseBodyAsString());
                    Map<String, Object> body = new HashMap<>();
                    body.put("status", e.getStatusCode().value());
                    body.put("body", e.getResponseBodyAsString());
                    return Mono.just(DispatchResult.fail("Meta " + e.getStatusCode() + ": " + e.getMessage(), body));
                })
                .onErrorResume(t -> Mono.just(DispatchResult.fail("Meta dispatch failed: " + t.getMessage(), null)));
    }

    /** Builds the {@code {data:[{...}]}} payload per Meta CAPI Part 5.3. */
    private Map<String, Object> buildPayload(ConversionEvent event, ConversionActionMapping mapping, Ticket ticket) {

        Map<String, Object> userData = buildUserData(event, ticket);
        Map<String, Object> customData = buildCustomData(event, mapping);

        Map<String, Object> singleEvent = new HashMap<>();
        singleEvent.put("event_name", event.getEventName());
        singleEvent.put("event_time", Instant.now().getEpochSecond());
        singleEvent.put("event_id", event.getEventId());
        singleEvent.put("action_source", event.getActionSource().getWireValue());
        singleEvent.put("user_data", userData);
        if (!customData.isEmpty()) singleEvent.put("custom_data", customData);

        Map<String, Object> root = new HashMap<>();
        root.put("data", List.of(singleEvent));
        return root;
    }

    private Map<String, Object> buildUserData(ConversionEvent event, Ticket ticket) {

        Map<String, Object> ud = new HashMap<>();

        if (ticket.getEmail() != null && !ticket.getEmail().isBlank()) {
            ud.put("em", List.of(ConversionsApiHashUtil.hashEmail(ticket.getEmail())));
        }
        if (ticket.getPhoneNumber() != null && !ticket.getPhoneNumber().isBlank()) {
            String fullPhone = (ticket.getDialCode() == null ? "" : ticket.getDialCode().toString()) + ticket.getPhoneNumber();
            ud.put("ph", List.of(ConversionsApiHashUtil.hashPhone(fullPhone)));
        }

        Map<String, Object> adData = ticket.getAdData();
        boolean isWebsite = event.getActionSource() == ConversionActionSource.WEBSITE;

        if (isWebsite && adData != null) {
            // fbc/fbp — Part 6.6: same cookies travel across Lead/MQL/SQL since they're stored on the ticket
            Object fbc = adData.get("fbc");
            Object fbp = adData.get("fbp");
            if (fbc != null) ud.put("fbc", fbc);
            if (fbp != null) ud.put("fbp", fbp);

            // Optional IP/UA — Part 6.4: website only, NEVER for system_generated
            Object ip = adData.get("client_ip_address");
            Object ua = adData.get("client_user_agent");
            if (ip != null) ud.put("client_ip_address", ip);
            if (ua != null) ud.put("client_user_agent", ua);
        }

        if (!isWebsite && adData != null) {
            // lead_id — Part 6.3: in user_data, not custom_data
            Object leadId = adData.getOrDefault("lead_id", adData.get("leadgen_id"));
            if (leadId != null) ud.put("lead_id", leadId);
        }

        return ud;
    }

    private Map<String, Object> buildCustomData(ConversionEvent event, ConversionActionMapping mapping) {
        // Part 6.5: only lead_stage + minimal extras. NO value/currency/content_*.
        Map<String, Object> cd = new HashMap<>();
        cd.put("lead_stage", event.getEventName());
        if (mapping.getDefaultValue() != null) cd.put("value", mapping.getDefaultValue());
        if (mapping.getCurrency() != null) cd.put("currency", mapping.getCurrency());
        return cd;
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
