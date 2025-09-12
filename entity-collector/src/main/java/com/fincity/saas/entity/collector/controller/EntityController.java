package com.fincity.saas.entity.collector.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.saas.entity.collector.dto.WebsiteDetails;
import com.fincity.saas.entity.collector.service.EntityCollectorService;
import com.fincity.saas.entity.collector.util.MetaEntityUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/entity/collector/entry")
public class EntityController {

    private final EntityCollectorService entityCollectorService;

    @Value("${meta.webhook.verify-token:null}")
    private String token;

    public EntityController(EntityCollectorService entityCollectorService) {
        this.entityCollectorService = entityCollectorService;
    }

    @GetMapping("/social/facebook")
    public Mono<ResponseEntity<String>> verifyMetaWebhook(
            @RequestParam(name = "hub.mode") String mode,
            @RequestParam(name = "hub.verify_token") String verifyToken,
            @RequestParam(name = "hub.challenge") String challenge) {

        return MetaEntityUtil.verifyMetaWebhook(mode, verifyToken, challenge, token);
    }

    @PostMapping("/social/facebook")
    public Mono<Void> handleMetaEntity(@RequestBody JsonNode webhookData) {
        return entityCollectorService.processMetaFormEntity(webhookData);
    }

    @PostMapping("/website")
    public Mono<Void> handleWebsiteEntity(@RequestBody WebsiteDetails requestBodyMono, ServerHttpRequest request) {
        return entityCollectorService.processWebsiteFormEntity(requestBodyMono, request);
    }
}
