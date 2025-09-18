package com.fincity.saas.entity.collector.util;

import static com.fincity.saas.entity.collector.util.EntityUtil.populateStaticFields;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fincity.saas.entity.collector.enums.LeadSource;
import com.fincity.saas.entity.collector.enums.LeadSubSource;
import com.fincity.saas.entity.collector.model.LeadDetails;
import com.fincity.saas.entity.collector.enums.MetaLeadFieldType;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.collector.dto.CampaignDetails;
import com.fincity.saas.entity.collector.dto.EntityIntegration;
import com.fincity.saas.entity.collector.dto.EntityResponse;
import com.fincity.saas.entity.collector.service.EntityCollectorLogService;
import com.fincity.saas.entity.collector.service.EntityCollectorMessageResourceService;
import java.util.*;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public final class MetaEntityUtil {

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

    private static final WebClient webClient = WebClient.create();

    public static Mono<JsonNode> fetchMetaGraphData(String path, Map<String, String> queryParams) {
        return webClient
                .get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.scheme(SCHEME).host(META_HOST).path(path);
                    queryParams.forEach(builder::queryParam);
                    return builder.build();
                })
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

                                        if (formId != null && leadGenId != null && adId != null) {
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
            String adId,
            String token,
            EntityIntegration integration,
            EntityCollectorMessageResourceService messageService,
            EntityCollectorLogService logService,
            ULong logId) {

        return FlatMapUtil.flatMapMonoWithNull(
                        () -> buildCampaignDetails(adId, token),
                        campaignDetails -> buildLeadDetails(incomingLead, formDetails),
                        (campaignDetails, leadDetails) -> Mono.just(buildEntityResponse(leadDetails, campaignDetails, integration)),
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

    public static Mono<LeadDetails> buildLeadDetails(JsonNode incomingLead, JsonNode formDetails) {

        return FlatMapUtil.flatMapMonoWithNull(

                () -> Mono.just(new ObjectMapper()),

                mapper -> {
                    ObjectNode leadNode = JsonNodeFactory.instance.objectNode();
                    ObjectNode customFieldsNode = JsonNodeFactory.instance.objectNode();
                    Map<String, String> typeMapping = new HashMap<>();
                    Map<String, String> labelMapping = new HashMap<>();

                    for (JsonNode question : formDetails.path(META_QUESTION)) {
                        String key = question.path(KEY).asText();
                        typeMapping.put(key, question.path(TYPE).asText());
                        labelMapping.put(key, question.path(LABEL).asText());
                    }

                    for (JsonNode field : incomingLead.path(FIELD_DATA)) {
                        String key = field.path(NAME).asText();
                        String value = field.path(VALUES).isArray()
                                        && !field.path(VALUES).isEmpty()
                                ? field.path(VALUES).get(0).asText()
                                : "";

                        String type = typeMapping.get(key);
                        String label = labelMapping.get(key);

                        if (CUSTOM.equalsIgnoreCase(type)) {
                            customFieldsNode.put(label, value);
                        } else {
                            MetaLeadFieldType fieldType = MetaLeadFieldType.fromType(type);
                            if (fieldType != null) {
                                leadNode.put(fieldType.getFieldName(), value);
                            }
                        }
                    }

                    Map<String, Object> customFields =
                            mapper.convertValue(customFieldsNode, new TypeReference<Map<String, Object>>() {});
                    LeadDetails lead = mapper.convertValue(leadNode, LeadDetails.class);
                    lead.setCustomFields(customFields);
                    populateStaticFields(lead, FACEBOOK, LeadSource.SOCIAL_MEDIA, LeadSubSource.FACEBOOK);

                    return Mono.just(lead);
                },
                (mapper, leadDetails) -> Mono.just(leadDetails));
    }

    public static Mono<ResponseEntity<String>> verifyMetaWebhook(String mode, String verifyToken, String challenge, String token) {
        return Mono.just(
                SUBSCRIBE.equals(mode) && token.equals(verifyToken)
                        ? ResponseEntity.ok(challenge)
                        : ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification token mismatch"));
    }

    public record ExtractPayload(String formId, String leadGenId, String adId) {}
}
