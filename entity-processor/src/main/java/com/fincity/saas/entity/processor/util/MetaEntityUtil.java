package com.fincity.saas.entity.processor.util;

import static com.fincity.saas.entity.processor.util.EntityUtil.populateStaticFields;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fincity.saas.entity.processor.enums.LeadSource;
import com.fincity.saas.entity.processor.enums.LeadSubSource;
import com.fincity.saas.entity.processor.model.LeadDetails;
import com.fincity.saas.entity.processor.enums.MetaLeadFieldType;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.dto.CampaignDetails;
import com.fincity.saas.entity.processor.dto.EntityIntegration;
import com.fincity.saas.entity.processor.dto.EntityResponse;
import com.fincity.saas.entity.processor.service.EntityCollectorLogService;
import com.fincity.saas.entity.processor.service.EntityCollectorMessageResourceService;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public final class MetaEntityUtil {

    private static final Logger log = LoggerFactory.getLogger(MetaEntityUtil.class);

    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String META_HOST = "graph.facebook.com";
    private static final String SCHEME = "https";
    private static final String META_VERSION = "/v22.0/";
    private static final String ACCESS_TOKEN = "access_token";
    private static final String META_FIELD = "fields";
    private static final String META_QUESTION = "questions";
    private static final String KEY = "key";
    private static final String TYPE = "type";
    private static final String LABEL = "label";
    private static final String VALUES = "values";
    private static final String CUSTOM = "CUSTOM";
    private static final String FORM_ID = "form_id";
    private static final String LEADGEN_ID = "leadgen_id";
    private static final String FIELD_DATA = "field_data";
    private static final String ENTRY = "entry";
    private static final String CHANGES = "changes";
    private static final String SUBSCRIBE = "subscribe";
    private static final String VALUE = "value";
    private static final String AD_ID = "ad_id";
    private static final String ADSET = "adset";
    private static final String CAMPAIGN = "campaign";
    private static final String AD_FIELDS = "id,name,adset,campaign";
    private static final String BASIC_ENTITY_FIELDS = "id,name";
    private static final String FACEBOOK = "facebook";

    // formData payload keys; see Ticket.FORM_DATA_* for the ticket-side mirror.
    private static final String FD_PROVIDER = "provider";
    private static final String FD_FORM_ID = "formId";
    private static final String FD_LEADGEN_ID = "leadGenId";
    private static final String FD_STANDARD = "standard";
    private static final String FD_CUSTOM = "custom";
    private static final String FD_RAW = "raw";
    private static final String FD_RAW_FIELD_DATA = "fieldData";
    private static final String FD_RAW_QUESTIONS = "questions";
    private static final String FACEBOOK_FORM_PROVIDER = "FACEBOOK_FORM";

    // Multi-answer questions are kept as a list in formData, but the typed LeadDetails
    // fields are all String, so they get a joined value instead.
    private static final String MULTI_VALUE_SEPARATOR = ", ";

    private static final TypeReference<List<Map<String, Object>>> RAW_LIST_TYPE = new TypeReference<>() {};

    // Default body buffer in Spring WebClient is 256 KB which Google Ads insights
    // responses (yearly daily-segmented data) blow past. Bump to 16 MB so large
    // GAQL responses don't fail with DataBufferLimitException.
    private static final WebClient webClient = WebClient.builder()
            .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();

    public static Mono<JsonNode> fetchMetaGraphData(String path, Map<String, String> queryParams) {
        // Build a fully-encoded URI directly. We avoid the WebClient `uri(uriBuilder -> ...)`
        // lambda form because Spring's DefaultUriBuilderFactory re-applies URI template
        // expansion on the returned URI, which mis-parses values containing `{...}` (e.g.
        // Meta's `time_range={"since":"...","until":"..."}` JSON parameter) as
        // template variables and throws "Not enough variable values available to expand".
        String query = queryParams.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        URI uri = URI.create(SCHEME + "://" + META_HOST + path + (query.isEmpty() ? "" : "?" + query));
        return webClient
                .get()
                .uri(uri)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    /**
     * POST to Meta Graph API as form-encoded body (the shape Meta expects for write
     * operations like creating a dataset/pixel). Same URI-encoding guardrails as
     * {@link #fetchMetaGraphData}.
     */
    public static Mono<JsonNode> postMetaGraphData(String path, Map<String, String> formParams) {
        URI uri = URI.create(SCHEME + "://" + META_HOST + path);
        String body = formParams.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return webClient
                .post()
                .uri(uri)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    private static Map<String, String> buildParams(String token, String fields) {
        return Map.of(ACCESS_TOKEN, token, META_FIELD, fields);
    }

    public static Mono<JsonNode> fetchMetaAdDetails(String adId, String token) {
        return fetchMetaGraphData(META_VERSION + adId, buildParams(token, AD_FIELDS));
    }

    public static Mono<JsonNode> fetchMetaCampaignDetails(String campaignId, String token) {
        return fetchMetaGraphData(META_VERSION + campaignId, buildParams(token, BASIC_ENTITY_FIELDS));
    }

    public static Mono<JsonNode> fetchMetaAdSetDetails(String adSetId, String token) {
        return fetchMetaGraphData(META_VERSION + adSetId, buildParams(token, BASIC_ENTITY_FIELDS));
    }

    public static Mono<Tuple2<JsonNode, JsonNode>> fetchMetaData(
            String leadGenId, String formId, String token, EntityCollectorLogService logService, ULong logId) {

        return FlatMapUtil.flatMapMono(

                        () -> fetchMetaGraphData(META_VERSION + leadGenId, Map.of(ACCESS_TOKEN, token)),

                        leadData -> fetchMetaGraphData(
                                META_VERSION + formId, Map.of(ACCESS_TOKEN, token, META_FIELD, META_QUESTION)),

                        (leadData, formData) -> Mono.just(Tuples.of(leadData, formData)))
                .onErrorResume(error ->
                        logService.updateOnError(logId, error.getMessage()).then(Mono.empty()));
    }

    /**
     * Compact, log-safe preview of a Meta webhook payload. Caps at ~300 chars
     * so a noisy payload doesn't flood the log on the residual-leak branch.
     */
    private static String previewPayload(JsonNode payload) {
        if (payload == null || payload.isMissingNode() || payload.isNull()) return "<empty>";
        String s = payload.toString();
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    private static EntityResponse buildEntityResponse(LeadDetails lead, CampaignDetails campaignDetails, EntityIntegration integration) {
        EntityResponse response = new EntityResponse();
        response.setLeadDetails(lead);
        response.setCampaignDetails(campaignDetails);
        response.setAppCode(integration.getOutAppCode());
        response.setClientCode(integration.getClientCode());
        return response;
    }

    public static Mono<List<ExtractPayload>> extractMetaPayload(JsonNode payload) {

        return FlatMapUtil.flatMapMono(

                        () -> Mono.justOrEmpty(payload).filter(p -> p.has(ENTRY)),

                        validPayload -> {

                            List<ExtractPayload> resultList = new ArrayList<>();

                            validPayload.get(ENTRY).forEach(entry -> entry.path(CHANGES)
                                    .forEach(change -> {
                                        JsonNode value = change.path(VALUE);
                                        String formId = value.path(FORM_ID).asText(null);
                                        String leadGenId =
                                                value.path(LEADGEN_ID).asText(null);
                                        String adId = value.path(AD_ID).asText(null);

                                        if (formId != null && leadGenId != null) {
                                            resultList.add(new ExtractPayload(formId, leadGenId, adId));
                                        }
                                    }));
                            return Mono.justOrEmpty(resultList).filter(list -> !list.isEmpty());
                        },
                        (validPayload, resultList) -> Mono.just(resultList))
                .defaultIfEmpty(Collections.emptyList());
    }

    public static Mono<EntityResponse> normalizeMetaEntity(
            JsonNode incomingLead,
            JsonNode formDetails,
            ExtractPayload extract,
            String token,
            EntityIntegration integration,
            EntityCollectorMessageResourceService messageService,
            EntityCollectorLogService logService,
            ULong logId) {

        String adId = extract != null ? extract.adId() : null;
        String leadGenId = extract != null ? extract.leadGenId() : null;

        return FlatMapUtil.flatMapMonoWithNull(
                        () -> buildCampaignDetails(adId, token),
                        campaignDetails -> buildLeadDetails(incomingLead, formDetails, extract),
                        (campaignDetails, leadDetails) -> {
                            // Stamp the leadgen id onto adData so Meta Conversions API can pick it up
                            // as user_data.lead_id for system_generated events (Meta CAPI Part 6.3).
                            // Source of truth: the leadGenId extracted from the webhook envelope
                            // (entry[].changes[].value.leadgen_id). We pass this through from
                            // EntityCollectorService rather than reading incomingLead.path("id") —
                            // incomingLead is the Graph API response, which occasionally returns
                            // payloads missing `id` (rate-limited / partial). Falling back to the
                            // Graph API id preserves backward compat if a caller passes null.
                            String stampId = (leadGenId != null && !leadGenId.isBlank())
                                    ? leadGenId
                                    : incomingLead.path(ID).asText(null);
                            if (stampId != null && !stampId.isBlank()) {
                                Map<String, Object> adData = leadDetails.getAdData() != null
                                        ? new HashMap<>(leadDetails.getAdData())
                                        : new HashMap<>();
                                adData.put("lead_id", stampId);
                                adData.put(LEADGEN_ID, stampId);
                                leadDetails.setAdData(adData);
                            } else {
                                // Both the webhook-extracted leadGenId AND the Graph API response's id
                                // were missing — this should be unreachable in practice since the
                                // webhook envelope is the source of truth. If this fires, something
                                // upstream of the collector dropped the leadgen_id.
                                List<String> topLevelKeys = new ArrayList<>();
                                incomingLead.fieldNames().forEachRemaining(topLevelKeys::add);
                                log.warn(
                                        "MetaEntityUtil: no leadGenId available (param null AND '{}'"
                                                + " missing on Graph API response) — cannot stamp lead_id."
                                                + " ad_id={} logId={} topLevelKeys={} payloadPreview={}",
                                        ID,
                                        adId,
                                        logId,
                                        topLevelKeys,
                                        previewPayload(incomingLead));
                            }
                            return Mono.just(buildEntityResponse(leadDetails, campaignDetails, integration));
                        },
                        (campaignDetails, leadDetails, response) -> Mono.just(response))
                .switchIfEmpty(messageService
                        .getMessage(EntityCollectorMessageResourceService.FAILED_NORMALIZE_ENTITY)
                        .flatMap(msg -> logService.updateOnError(logId, msg).then(Mono.empty())));
    }

    public static Mono<CampaignDetails> buildCampaignDetails(String adId, String token) {

        return FlatMapUtil.flatMapMono(
                () -> fetchMetaAdDetails(adId, token),
                ad -> fetchMetaCampaignDetails(ad.path(CAMPAIGN).path(ID).asText(), token),
                (ad, campaign) -> fetchMetaAdSetDetails(ad.path(ADSET).path(ID).asText(), token),
                (ad, campaign, adSetNode) -> {
                    CampaignDetails cd = new CampaignDetails();

                    cd.setAdId(ad.path(ID).asText());
                    cd.setAdName(ad.path(NAME).asText());
                    cd.setCampaignId(campaign.path(ID).asText());
                    cd.setCampaignName(campaign.path(NAME).asText());
                    cd.setAdSetId(adSetNode.path(ID).asText());
                    cd.setAdSetName(adSetNode.path(NAME).asText());

                    return Mono.just(cd);
                });
    }

    public static Mono<LeadDetails> buildLeadDetails(
            JsonNode incomingLead, JsonNode formDetails, ExtractPayload extract) {

        return FlatMapUtil.flatMapMonoWithNull(

                () -> Mono.just(new ObjectMapper()),

                mapper -> {
                    ObjectNode leadNode = JsonNodeFactory.instance.objectNode();
                    Map<String, String> typeMapping = new HashMap<>();
                    Map<String, String> labelMapping = new HashMap<>();

                    for (JsonNode question : formDetails.path(META_QUESTION)) {
                        String key = question.path(KEY).asText();
                        typeMapping.put(key, question.path(TYPE).asText());
                        labelMapping.put(key, question.path(LABEL).asText());
                    }

                    // Normalized views of the submission that get persisted on the ticket.
                    // `standard` mirrors the typed LeadDetails fields; `custom` holds the custom
                    // questions plus anything whose question type we could not map.
                    Map<String, Object> standard = new LinkedHashMap<>();
                    Map<String, Object> custom = new LinkedHashMap<>();

                    for (JsonNode field : incomingLead.path(FIELD_DATA)) {
                        String key = field.path(NAME).asText();

                        // Meta returns every answer as an array. Multi-select questions carry more
                        // than one entry, so keep them all rather than just values[0].
                        List<String> values = new ArrayList<>();
                        field.path(VALUES).forEach(value -> values.add(value.asText()));

                        Object answer = switch (values.size()) {
                            case 0 -> "";
                            case 1 -> values.getFirst();
                            default -> List.copyOf(values);
                        };

                        // The typed LeadDetails fields are all String, so a multi-answer question
                        // gets a joined value there; the list form survives in formData.
                        String flatValue = String.join(MULTI_VALUE_SEPARATOR, values);

                        String type = typeMapping.get(key);
                        String label = labelMapping.get(key);

                        MetaLeadFieldType fieldType =
                                CUSTOM.equalsIgnoreCase(type) ? null : MetaLeadFieldType.fromType(type);

                        if (fieldType != null) {
                            leadNode.put(fieldType.getFieldName(), flatValue);
                            standard.put(fieldType.getFieldName(), answer);
                            continue;
                        }

                        // Three cases land here: CUSTOM questions, question types outside
                        // MetaLeadFieldType, and answers whose field_data[].name never matched a
                        // questions[].key (so `type` is null). Only the first used to be kept;
                        // the other two were discarded with no trace.
                        String customKey = !StringUtil.safeIsBlank(label) ? label : key;
                        custom.put(customKey, answer);

                        if (!CUSTOM.equalsIgnoreCase(type))
                            log.warn(
                                    "MetaEntityUtil: unmapped Meta question type '{}' for field_data"
                                            + " key '{}' (formId={}). Captured into formData.custom"
                                            + " under '{}' instead of being dropped.",
                                    type,
                                    key,
                                    extract != null ? extract.formId() : null,
                                    customKey);
                    }

                    LeadDetails lead = mapper.convertValue(leadNode, LeadDetails.class);
                    lead.setCustomFields(custom);
                    lead.setFormData(buildFormData(mapper, extract, standard, custom, incomingLead, formDetails));
                    populateStaticFields(lead, FACEBOOK, LeadSource.SOCIAL_MEDIA, LeadSubSource.FACEBOOK);

                    return Mono.just(lead);
                },
                (mapper, leadDetails) -> Mono.just(leadDetails));
    }

    /**
     * Assembles the {@code formData} payload persisted on {@code entity_processor_tickets.FORM_DATA}:
     * provenance, the two normalized answer maps, and a verbatim Graph API snapshot. The snapshot
     * means an answer we failed to normalize is still recoverable and a submission can be
     * re-examined without calling Meta again.
     */
    private static Map<String, Object> buildFormData(
            ObjectMapper mapper,
            ExtractPayload extract,
            Map<String, Object> standard,
            Map<String, Object> custom,
            JsonNode incomingLead,
            JsonNode formDetails) {

        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put(FD_PROVIDER, FACEBOOK_FORM_PROVIDER);

        if (extract != null) {
            if (!StringUtil.safeIsBlank(extract.formId())) formData.put(FD_FORM_ID, extract.formId());
            if (!StringUtil.safeIsBlank(extract.leadGenId())) formData.put(FD_LEADGEN_ID, extract.leadGenId());
        }

        formData.put(FD_STANDARD, standard);
        formData.put(FD_CUSTOM, custom);

        Map<String, Object> raw = new LinkedHashMap<>();
        if (incomingLead != null && incomingLead.path(FIELD_DATA).isArray())
            raw.put(FD_RAW_FIELD_DATA, mapper.convertValue(incomingLead.path(FIELD_DATA), RAW_LIST_TYPE));
        if (formDetails != null && formDetails.path(META_QUESTION).isArray())
            raw.put(FD_RAW_QUESTIONS, mapper.convertValue(formDetails.path(META_QUESTION), RAW_LIST_TYPE));
        if (!raw.isEmpty()) formData.put(FD_RAW, raw);

        return formData;
    }

    public static Mono<ResponseEntity<String>> verifyMetaWebhook(String mode, String verifyToken, String challenge, String token) {
        return Mono.just(
                SUBSCRIBE.equals(mode) && token.equals(verifyToken)
                        ? ResponseEntity.ok(challenge)
                        : ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification token mismatch"));
    }

    public record ExtractPayload(String formId, String leadGenId, String adId) {}
}
