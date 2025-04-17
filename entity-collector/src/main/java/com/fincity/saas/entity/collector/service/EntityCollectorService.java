package com.fincity.saas.entity.collector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.entity.collector.dto.EntityIntegration;
import com.fincity.saas.entity.collector.fiegn.IFeignCoreService;
import com.fincity.saas.entity.collector.jooq.enums.EntityIntegrationsInSourceType;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

@Slf4j
@Service
@AllArgsConstructor
public class EntityCollectorService {

    private final EntityIntegrationService entityIntegrationService;
    private final IFeignCoreService coreService;
    private static final String CONNECTION_NAME = "meta_facebook_connection";
    private final WebClient webClient = WebClient.create();

    public Mono<JsonNode> handleFaceBookEntity(JsonNode responseBody) {

        EntityCollectorUtilService.ExtractPayload payload =
                EntityCollectorUtilService.extractFacebookLeadPayload(responseBody);

        Set<String> formIds = payload.getFormIds();
        String leadGenId = payload.getLeadGenId();
        ObjectMapper mapper = new ObjectMapper();

        return Flux.fromIterable(formIds)
                .flatMap(formId ->
                        entityIntegrationService.findByInSourceAndType(formId, EntityIntegrationsInSourceType.FACEBOOK_FORM)
                )
                .collectList()
                .flatMap(integrations -> {
                    if (integrations.isEmpty()) {
                        return Mono.error(new RuntimeException("No matching entity integration found for any form_id"));
                    }

                    EntityIntegration integration = integrations.getFirst();

                    return this.fetchOAuthToken(integration.getClientCode(), integration.getAppCode())
                            .flatMap(token -> {
                                String formId = payload.getFormIds().iterator().next();

                                return this.fetchLeadDetails(leadGenId, token)
                                        .flatMap(leadDetails ->
                                                this.fetchFormDetails(formId, token)
                                                        .map(formDetails -> {
                                                            Object normalized = EntityCollectorUtilService.normalizedLeadObject(leadDetails, formDetails);
                                                            return mapper.valueToTree(normalized);
                                                        })
                                        );
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

    private Mono<JsonNode> fetchLeadDetails(String leadGenId, String accessToken) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("graph.facebook.com")
                        .path("/v22.0/{leadId}")
                        .queryParam("access_token", accessToken)
                        .build(leadGenId))
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    private Mono<JsonNode> fetchFormDetails(String formId, String accessToken) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("graph.facebook.com")
                        .path("/v22.0/{formId}")
                        .queryParam("fields", "questions")
                        .queryParam("access_token", accessToken)
                        .build(formId))
                .retrieve()
                .bodyToMono(JsonNode.class);
    }


}
