package com.fincity.saas.entity.collector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

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
}
