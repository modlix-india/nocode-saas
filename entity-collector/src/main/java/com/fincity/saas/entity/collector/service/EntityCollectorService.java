package com.fincity.saas.entity.collector.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fincity.saas.entity.collector.dto.EntityIntegration;
import com.fincity.saas.entity.collector.fiegn.IFeignCoreService;
import com.fincity.saas.entity.collector.jooq.enums.EntityCollectorLogStatus;
import com.fincity.saas.entity.collector.jooq.enums.EntityIntegrationsInSourceType;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@AllArgsConstructor
public class EntityCollectorService {

    private final EntityIntegrationService entityIntegrationService;
    private final IFeignCoreService coreService;
    private final EntityCollectorLogService entityCollectorLogService;
    private static final String CONNECTION_NAME = "meta_facebook_connection";

    private final WebClient webClient = WebClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    public Mono<JsonNode> handleMetaEntity(JsonNode responseBody) {

        EntityCollectorUtilService.ExtractPayload payload =
                EntityCollectorUtilService.extractFacebookLeadPayload(responseBody);

        Set<String> formIds = payload.getFormIds();
        String leadGenId = payload.getLeadGenId();

        return Flux.fromIterable(formIds)
                .flatMap(formId -> entityIntegrationService
                        .findByInSourceAndType(formId, EntityIntegrationsInSourceType.FACEBOOK_FORM))
                .collectList()
                .flatMap(integrations -> {
                    if (integrations.isEmpty()) {
                        return Mono.error(new RuntimeException("No matching entity integration found for any form_id"));
                    }

                    EntityIntegration integration = integrations.getFirst();

                    return fetchOAuthToken(integration.getClientCode(), integration.getAppCode())
                            .flatMap(token -> {
                                String formId = payload.getFormIds().iterator().next();

                                return Mono.zip(
                                        fetchMetaGraphData("/v22.0/" + leadGenId, Map.of("access_token", token)),
                                        fetchMetaGraphData("/v22.0/" + formId, Map.of("fields", "questions", "access_token", token))
                                ).flatMap(tuple -> {
                                    JsonNode leadDetails = tuple.getT1();
                                    JsonNode formDetails = tuple.getT2();

                                    Object normalized = EntityCollectorUtilService.normalizedLeadObject(leadDetails, formDetails);
                                    JsonNode normalizedEntity = mapper.valueToTree(normalized);

                               return Mono.just(normalizedEntity);
                                });
                            });
                });
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

    private Mono<JsonNode> fetchMetaGraphData(String path, Map<String, String> queryParams) {
        WebClient.RequestHeadersUriSpec<?> uriSpec = webClient.get();

        WebClient.RequestHeadersSpec<?> headersSpec = uriSpec.uri(uriBuilder -> {
            var builder = uriBuilder.scheme("https").host("graph.facebook.com").path(path);
            queryParams.forEach(builder::queryParam);
            return builder.build();
        });
        return headersSpec.retrieve().bodyToMono(JsonNode.class);
    }

    public Mono<JsonNode> handleWebsiteEntity(JsonNode websiteEntity) {

        String inSource = websiteEntity.path("source").asText(null);

        if (inSource == null) {
            return Mono.error(new RuntimeException("inSource is missing in the website lead payload"));
        }

        return entityIntegrationService.findByInSourceAndType(inSource, EntityIntegrationsInSourceType.WEBSITE)
                .switchIfEmpty(Mono.error(new RuntimeException("No matching entity integration found for inSource: " + inSource)))
                .map(integration -> {
                    String clientCode = integration.getClientCode();
                    String appCode = integration.getAppCode();

                    ObjectNode responseEntity = mapper.createObjectNode();
                    responseEntity.setAll((ObjectNode) websiteEntity);
                    responseEntity.put("clientCode", clientCode);
                    responseEntity.put("appCode", appCode);

                    return responseEntity;
                });
    }
}
