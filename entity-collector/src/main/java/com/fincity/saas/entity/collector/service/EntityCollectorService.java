package com.fincity.saas.entity.collector.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fincity.saas.entity.collector.dto.EntityIntegration;
import com.fincity.saas.entity.collector.fiegn.IFeignCoreService;
import com.fincity.saas.entity.collector.jooq.enums.EntityCollectorLogStatus;
import com.fincity.saas.entity.collector.jooq.enums.EntityIntegrationsInSourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class EntityCollectorService {

    private final EntityIntegrationService entityIntegrationService;
    private final IFeignCoreService coreService;
    private final EntityCollectorLogService entityCollectorLogService;
    private static final String CONNECTION_NAME = "meta_facebook_connection";

    private final WebClient webClient = WebClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${entity-collector.meta.webhook.verify-token}")
    private String token;

    public EntityCollectorService(EntityIntegrationService entityIntegrationService, IFeignCoreService coreService, EntityCollectorLogService entityCollectorLogService) {
        this.entityIntegrationService = entityIntegrationService;
        this.coreService = coreService;
        this.entityCollectorLogService = entityCollectorLogService;
    }


    public Mono<ResponseEntity<String>> verifyMetaWebhook(String mode, String verifyToken, String challenge) {
        if ("subscribe".equals(mode) && token.equals(verifyToken)) {
            return Mono.just(ResponseEntity.ok(challenge));
        } else {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification token mismatch"));
        }
    }


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

                    return entityCollectorLogService
                            .createInitialLog(
                                    integration.getId(),
                                    mapper.convertValue(responseBody, new TypeReference<>() {}),
                                    null
                            )
                            .flatMap(logId -> fetchOAuthToken(integration.getClientCode(), integration.getAppCode())
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

                                            return sendLeadToTarget(integration, normalizedEntity)
                                                    .then(
                                                            entityCollectorLogService.updateLogWithOutgoingEntity(
                                                                    logId,
                                                                    mapper.convertValue(normalizedEntity, new TypeReference<>() {}),
                                                                    EntityCollectorLogStatus.SUCCESS,
                                                                    "Entity processed and sent successfully"
                                                            ).thenReturn(normalizedEntity)
                                                    );
                                        });
                                    })
                                    .onErrorResume(ex -> entityCollectorLogService
                                            .updateLogStatus(
                                                    logId,
                                                    EntityCollectorLogStatus.REJECTED,
                                                    "Error processing meta entity: " + ex.getMessage()
                                            )
                                            .then(Mono.error(ex))
                                    )
                            );
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

        Map<String, Object> incomingData = mapper.convertValue(websiteEntity, new TypeReference<>() {});

        return entityIntegrationService.findByInSourceAndType(inSource, EntityIntegrationsInSourceType.WEBSITE)
                .switchIfEmpty(Mono.error(new RuntimeException("No matching entity integration found for inSource: " + inSource)))
                .flatMap(integration ->
                        entityCollectorLogService.createInitialLog(integration.getId(), incomingData, null)
                                .flatMap(logId -> {
                                    try {

                                        ObjectNode enrichedEntity = mapper.createObjectNode();
                                        enrichedEntity.setAll((ObjectNode) websiteEntity);
                                        enrichedEntity.put("clientCode", integration.getClientCode());
                                        enrichedEntity.put("appCode", integration.getAppCode());

//                                        Map<String, Object> outgoingData = mapper.convertValue(enrichedEntity, new TypeReference<>() {});
                                        JsonNode enrichedJsonNode = mapper.valueToTree(enrichedEntity);

                                        return sendLeadToTarget(integration, enrichedJsonNode)
                                                .then(
                                                        entityCollectorLogService.updateLogWithOutgoingEntity(
                                                                logId,
                                                                mapper.convertValue(enrichedJsonNode, new TypeReference<>() {}),
                                                                EntityCollectorLogStatus.SUCCESS,
                                                                "Website entity processed and sent successfully"
                                                        ).thenReturn(enrichedJsonNode)
                                                );

                                    } catch (Exception ex) {
                                        return entityCollectorLogService
                                                .updateLogStatus(
                                                        logId,
                                                        EntityCollectorLogStatus.REJECTED,
                                                        "Error processing website entity: " + ex.getMessage()
                                                )
                                                .then(Mono.error(ex));
                                    }
                                }));
    }

    public Mono<Void> sendLeadToTarget(EntityIntegration integration, JsonNode leadData) {
        if (integration.getTarget() == null || integration.getTarget().isBlank()) {
            return Mono.error(new RuntimeException("No target URL configured in entity integration."));
        }

        WebClient webClient = WebClient.create();
        Mono<Void> primarySend = sendToUrl(webClient, integration.getTarget(), leadData);
        Mono<Void> secondarySend = Optional.ofNullable(integration.getSecondaryTarget())
                .filter(s -> !s.isBlank())
                .map(url -> sendToUrl(webClient, url, leadData))
                .orElse(Mono.empty());

        return Mono.when(primarySend, secondarySend);
    }

    private Mono<Void> sendToUrl(WebClient webClient, String url, JsonNode body) {
        return webClient.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .then();
    }

//    private  Mono<Boolean> verifyTarget(EntityIntegration integration) {
//        if (integration.getVerifyToken() == null || integration.getTarget() == null) {
//            return Mono.just(true);
//        }
//
//
//        URI uri
//    }

}
