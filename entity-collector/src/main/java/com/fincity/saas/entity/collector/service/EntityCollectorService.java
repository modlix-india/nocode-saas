package com.fincity.saas.entity.collector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.saas.entity.collector.dto.CollectionLog;
import com.fincity.saas.entity.collector.dto.EntityIntegration;
import com.fincity.saas.entity.collector.fiegn.IFeignCoreService;
import com.fincity.saas.entity.collector.jooq.enums.CollectionLogsStatus;
import com.fincity.saas.entity.collector.jooq.enums.EntityIntegrationsInSourceType;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.types.ULong;
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
    private final CollectionLogService collectionLogService;
    private static final String CONNECTION_NAME = "meta_facebook_connection";
    private final WebClient webClient = WebClient.create();

    public Mono<JsonNode> handleFaceBookEntity(JsonNode responseBody) {

        EntityCollectorUtilService.ExtractPayload payload =
                EntityCollectorUtilService.extractFacebookLeadPayload(responseBody);

        Set<String> formIds = payload.getFormIds();
        String leadGenId = payload.getLeadGenId();

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
                                if (token == null || token.isEmpty()) {
                                    return Mono.error(new RuntimeException("Failed to fetch OAuth token"));
                                }
                                return this.fetchLeadDetails(leadGenId, token);
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

    private Mono<CollectionLog> logLeadTransfer(ULong entityIntegrationId, JsonNode incomingLead, String ipAddress, JsonNode outgoingLead, CollectionLogsStatus status, String statusMessage){
        CollectionLog log = new CollectionLog();
        log.setEntityIntegrationId(entityIntegrationId);
        log.setIncomingLeadData(incomingLead);
        log.setIpAddress(ipAddress);
        log.setOutgoingLeadData(outgoingLead);
        log.setStatus(status);
        log.setStatusMessage(statusMessage);

        return this.collectionLogService.create(log);

    }

}
