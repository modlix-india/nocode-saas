package com.fincity.saas.entity.collector.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fincity.saas.entity.collector.dto.EntityResponse;
import com.fincity.saas.entity.collector.enums.EntityFieldType;
import com.fincity.saas.entity.collector.fiegn.IFeignCoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.flywaydb.core.internal.util.ClassUtils.setFieldValue;


@Slf4j
public class MetaEntityUtil {

    private final IFeignCoreService coreService;

    private static final WebClient webClient = WebClient.create();

    private static final String META_VERSION = "/v22.0/";
    private static final String ACCESS_TOKEN = "access_token";
    public static final String CONNECTION_NAME = "meta_facebook_connection";
    private static final String META_FIELD = "fields";
    private static final String META_QUESTION = "questions";
    private static final  String KEY = "key";
    private static final String TYPE = "type";
    private static final String LABEL = "label";
    private static final String NAME = "name";
    private static final String VALUES = "values";
    private static final String CUSTOM = "CUSTOM";
    private static final String FORM_ID = "form_id";
    private static final String LEADGEN_ID = "leadgen_id";
    private static final String FIELD_DATA = "field_data";
    private static final String ENTRY = "entry";
    private static final String CHANGES = "changes";
    private static final String SUBSCRIBE = "subscribe";


    @Value("${entity-collector.meta.webhook.verify-token}")
    private String token;

    public MetaEntityUtil(IFeignCoreService coreService) {
        this.coreService = coreService;
    }


    public static Mono<JsonNode> fetchMetaGraphData(String path, Map<String, String> queryParams) {
        WebClient.RequestHeadersUriSpec<?> uriSpec = webClient.get();

        WebClient.RequestHeadersSpec<?> headersSpec = uriSpec.uri(uriBuilder -> {
            var builder = uriBuilder.scheme("https").host("graph.facebook.com").path(path);
            queryParams.forEach(builder::queryParam);
            return builder.build();
        });
        return headersSpec.retrieve().bodyToMono(JsonNode.class);
    }

    public static Mono<Tuple2<JsonNode, JsonNode>> fetchMetaData(String leadGenId, String formId, String token) {
        return fetchMetaGraphData(META_VERSION + leadGenId, Map.of(ACCESS_TOKEN, token))
                .flatMap(leadData -> fetchMetaGraphData(META_VERSION + formId,
                        Map.of(META_FIELD, META_QUESTION, ACCESS_TOKEN, token))
                        .map(formData -> Tuples.of(leadData, formData))
                );
    }


    public static EntityResponse normalizeMetaEntity(JsonNode leadDetails, JsonNode formDetails) {
        ObjectNode normalized = JsonNodeFactory.instance.objectNode();
        ObjectNode customFieldsObject = JsonNodeFactory.instance.objectNode();
        EntityResponse response =new EntityResponse();

        Map<String, String> typeMapping = new HashMap<>();
        Map<String, String> labelMapping = new HashMap<>();
        JsonNode questions = formDetails.path(META_QUESTION);

        for (JsonNode question : questions) {
            String key = question.path(KEY).asText();
            String type = question.path(TYPE).asText();
            String label = question.path(LABEL).asText();

            typeMapping.put(key, type);
            labelMapping.put(key, label);
        }

        JsonNode fieldData = leadDetails.path(FIELD_DATA);

        for (JsonNode field : leadDetails.path(FIELD_DATA)) {
            String key = field.path(NAME).asText();
            JsonNode values = field.path(VALUES);
            String value = values.isArray() && !values.isEmpty() ? values.get(0).asText() : "";

            String type = typeMapping.get(key);
            String label = labelMapping.get(key);

            if (CUSTOM.equalsIgnoreCase(type)) {
                customFieldsObject.put(label, value);
            } else {
                EntityFieldType fieldType = EntityFieldType.fromType(type);
                if (fieldType != null) {
                    setFieldValue(response, fieldType.getFieldName(), value);
                }

            }
        }

        response.setCustomFields(customFieldsObject);
        return response;
    }

    public Mono<ResponseEntity<String>> verifyMetaWebhook(String mode, String verifyToken, String challenge) {
        return Mono.just(
                SUBSCRIBE.equals(mode) && token.equals(verifyToken)
                        ? ResponseEntity.ok(challenge)
                        : ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification token mismatch")
        );
    }

    private Mono<String> fetchOAuthToken(String clientCode, String appCode) {
        return coreService.getConnectionOAuth2Token(
                "",
                "",
                "",
                clientCode,
                appCode,
                CONNECTION_NAME
        );
    }

    public record ExtractPayload(Set<String> formIds, String leadGenId) { }

    public static ExtractPayload extractMetaPayload(JsonNode payload) {
        if (payload == null || !payload.has(ENTRY)) {
            throw new RuntimeException("Invalid Facebook payload structure.");
        }

        Set<String> formIds = new HashSet<>();
        String[] leadGenIdHolder = new String[1];

        payload.get(ENTRY).forEach(entry ->
                entry.path(CHANGES).forEach(change -> {
                    JsonNode value = change.path(VALUES);
                    String formId = value.path(FORM_ID).asText(null);
                    if (formId != null) formIds.add(formId);

                    if (leadGenIdHolder[0] == null && value.has(LEADGEN_ID)) {
                        leadGenIdHolder[0] = value.get(LEADGEN_ID).asText();
                    }
                })
        );

        if (formIds.isEmpty()) throw new RuntimeException("No form_id found in Facebook payload.");
        if (leadGenIdHolder[0] == null) throw new RuntimeException("leadgen_id not found in Facebook payload.");

        return new ExtractPayload(formIds, leadGenIdHolder[0]);
    }

}
