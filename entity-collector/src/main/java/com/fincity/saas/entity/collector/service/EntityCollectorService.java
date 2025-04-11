package com.fincity.saas.entity.collector.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.saas.entity.collector.dto.EntityIntegration;
import com.fincity.saas.entity.collector.jooq.enums.EntityIntegrationsInSourceType;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@AllArgsConstructor
public class EntityCollectorService {

    private final EntityIntegrationService entityIntegrationService;

    public Mono<List<EntityIntegration>> handleFaceBookEntity(JsonNode responseBody) {

        Set<String> formIds = EntityCollectorUtilService.extractFormIds(responseBody);


        return Flux.fromIterable(formIds)
                .flatMap(formId ->
                        entityIntegrationService.findByInSourceAndType(formId, EntityIntegrationsInSourceType.FACEBOOK_FORM)
                )
                .collectList()
                .flatMap(integrations -> {
                    if (integrations.isEmpty()) {
                        return Mono.error(new RuntimeException("No matching entity integration found for any form_id"));
                    }
                    return Mono.just(integrations);
                });
    }
}
