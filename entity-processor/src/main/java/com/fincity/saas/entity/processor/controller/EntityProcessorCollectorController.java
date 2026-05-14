package com.fincity.saas.entity.processor.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.saas.entity.processor.jooq.enums.EntityProcessorIntegrationsInSourceType;
import com.fincity.saas.entity.processor.model.WebsiteDetails;
import com.fincity.saas.entity.processor.service.EntityCollectorService;
import com.fincity.saas.entity.processor.service.EntityIntegrationService;
import com.fincity.saas.entity.processor.util.MetaEntityUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/entity/processor/collector/entry")
public class EntityProcessorCollectorController {

    private final EntityCollectorService entityCollectorService;
    private final EntityIntegrationService entityIntegrationService;

    /** Fallback when no per-tenant integration matches; preserves pre-Phase-5 behaviour. */
    @Value("${meta.webhook.verify-token:null}")
    private String fallbackToken;

    public EntityProcessorCollectorController(
            EntityCollectorService entityCollectorService, EntityIntegrationService entityIntegrationService) {
        this.entityCollectorService = entityCollectorService;
        this.entityIntegrationService = entityIntegrationService;
    }

    @GetMapping("/social/facebook")
    public Mono<ResponseEntity<String>> verifyMetaWebhook(
            @RequestParam(name = "hub.mode") String mode,
            @RequestParam(name = "hub.verify_token") String verifyToken,
            @RequestParam(name = "hub.challenge") String challenge) {

        return this.entityIntegrationService
                .findActiveByVerifyToken(verifyToken, EntityProcessorIntegrationsInSourceType.FACEBOOK_FORM)
                .flatMap(integration -> MetaEntityUtil.verifyMetaWebhook(mode, verifyToken, challenge, verifyToken))
                .switchIfEmpty(MetaEntityUtil.verifyMetaWebhook(mode, verifyToken, challenge, fallbackToken));
    }

    @PostMapping("/social/facebook")
    public Mono<Void> handleMetaEntity(@RequestBody JsonNode webhookData) {
        return entityCollectorService.processMetaFormEntity(webhookData);
    }

    @PostMapping("/website")
    public Mono<Map<String, Object>> handleWebsiteEntity(@RequestBody WebsiteDetails requestBodyMono, ServerHttpRequest request) {
        return entityCollectorService.processWebsiteFormEntity(requestBodyMono, request);
    }
}
