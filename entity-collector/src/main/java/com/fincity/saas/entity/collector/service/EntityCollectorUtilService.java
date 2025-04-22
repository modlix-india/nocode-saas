package com.fincity.saas.entity.collector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fincity.saas.entity.collector.dto.EntityResponse;
import lombok.Getter;
import com.fincity.saas.entity.collector.enums.EntityFieldType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.flywaydb.core.internal.util.ClassUtils.setFieldValue;


public class EntityCollectorUtilService {

    @Getter
    public static class ExtractPayload {
        private final Set<String> formIds;
        private final String leadGenId;

        public ExtractPayload(Set<String> formIds, String leadGenId) {
            this.formIds = formIds;
            this.leadGenId = leadGenId;
        }
    }

    public static ExtractPayload extractFacebookLeadPayload(JsonNode payload) {
        Set<String> formIds = new HashSet<>();
        String leadGenId = null;

        if (payload == null || !payload.has("entry")) {
            throw new RuntimeException("Invalid Facebook payload structure.");
        }

        for (JsonNode entry : payload.get("entry")) {
            ArrayNode changes = (ArrayNode) entry.path("changes");
            for (JsonNode change : changes) {
                JsonNode value = change.path("value");

                String formId = value.path("form_id").asText(null);
                if (formId != null) {
                    formIds.add(formId);
                }

                if (leadGenId == null && value.has("leadgen_id")) {
                    leadGenId = value.get("leadgen_id").asText();
                }
            }
        }

        if (formIds.isEmpty()) throw new RuntimeException("No form_id found in Facebook payload.");
        if (leadGenId == null) throw new RuntimeException("leadgen_id not found in Facebook payload.");

        return new ExtractPayload(formIds, leadGenId);
    }

    public static EntityResponse normalizedLeadObject(JsonNode leadDetails, JsonNode formDetails) {
        ObjectNode normalized = JsonNodeFactory.instance.objectNode();
        ObjectNode customFieldsObject = JsonNodeFactory.instance.objectNode();
        EntityResponse response =new EntityResponse();

        Map<String, String> typeMapping = new HashMap<>();
        Map<String, String> labelMapping = new HashMap<>();
        JsonNode questions = formDetails.path("questions");

        for (JsonNode question : questions) {
            String key = question.path("key").asText();
            String type = question.path("type").asText();
            String label = question.path("label").asText();

            typeMapping.put(key, type);
            labelMapping.put(key, label);
        }

        JsonNode fieldData = leadDetails.path("field_data");

        for (JsonNode field : leadDetails.path("field_data")) {
            String key = field.path("name").asText();
            JsonNode values = field.path("values");
            String value = values.isArray() && !values.isEmpty() ? values.get(0).asText() : "";

            String type = typeMapping.get(key);
            String label = labelMapping.get(key);

            if ("CUSTOM".equalsIgnoreCase(type)) {
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

}

