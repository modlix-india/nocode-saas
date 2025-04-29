package com.fincity.saas.entity.collector.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fincity.saas.entity.collector.dto.EntityIntegration;
import reactor.core.publisher.Mono;

public class WebsiteEntityUtil {

    private static final String CLIENT_CODE = "clientCode";
    private static final String APP_CODE = "appCode";


    public static ObjectNode updateWebsiteEntity(ObjectMapper mapper, JsonNode websiteEntity, String clientCode, String appCode) {
        ObjectNode enrichedEntity = mapper.createObjectNode();
        enrichedEntity.setAll((ObjectNode) websiteEntity);
        enrichedEntity.put(CLIENT_CODE, clientCode);
        enrichedEntity.put(APP_CODE, appCode);
        return enrichedEntity;
    }

    public static Mono<Object> normalizeWebsiteEntity(JsonNode websiteEntityPayload, EntityIntegration integration) {
        return null;
    }
}

