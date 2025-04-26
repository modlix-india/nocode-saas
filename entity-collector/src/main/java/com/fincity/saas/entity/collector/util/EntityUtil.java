package com.fincity.saas.entity.collector.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.saas.entity.collector.dto.EntityIntegration;
import com.fincity.saas.entity.collector.fiegn.IFeignCoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Slf4j
public class EntityUtil {

    private static final WebClient webClient = WebClient.create();

    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";


    public static Mono<Void> sendEntityToTarget(EntityIntegration integration, JsonNode entityData) {
        if (integration.getPrimaryTarget() == null || integration.getPrimaryTarget().isBlank()) {
            return Mono.error(new RuntimeException("No target URL configured in entity integration."));
        }

        Mono<Void> primarySend = sendToUrl(integration.getPrimaryTarget(), entityData);
        Mono<Void> secondarySend = Optional.ofNullable(integration.getSecondaryTarget())
                .filter(s -> !s.isBlank())
                .map(url -> sendToUrl(url, entityData))
                .orElse(Mono.empty());

        return Mono.when(primarySend, secondarySend);
    }

    private static Mono<Void> sendToUrl(String url, JsonNode body) {
        return webClient.post()
                .uri(url)
                .header(CONTENT_TYPE, APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    public static Mono<String> fetchOAuthToken(IFeignCoreService coreService, String clientCode, String appCode, String connectionName) {
        return coreService.getConnectionOAuth2Token(
                "",
                "",
                "",
                clientCode,
                appCode,
                connectionName);
    }

}

