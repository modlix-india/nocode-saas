package com.fincity.saas.entity.collector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.HashSet;
import java.util.Set;

public class EntityCollectorUtilService {

    public static Set<String> extractFormIds(JsonNode payload) {
        Set<String> formIds = new HashSet<>();

        JsonNode entry = payload.path("entry").get(0);
        if (entry == null) return formIds;

        ArrayNode changes = (ArrayNode) entry.path("changes");
        for (JsonNode change : changes) {
            String formId = change.path("value").path("form_id").asText(null);
            if (formId != null) {
                formIds.add(formId);
            }
        }

        return formIds;
    }

}
