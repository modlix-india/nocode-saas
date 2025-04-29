package com.fincity.saas.entity.collector.controller;


import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.saas.entity.collector.service.EntityCollectorService;

import com.fincity.saas.entity.collector.util.MetaEntityUtil;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;


@RestController
@RequestMapping("/api/entity")
@AllArgsConstructor
public class EntityCollectorController {

    public final MetaEntityUtil metaEntityUtil;
    public final EntityCollectorService entityCollectorService;

    @GetMapping("/social/facebook")
    public Mono<ResponseEntity<String>> verifyMetaWebhook(
            @RequestParam(name = "hub.mode") String mode,
            @RequestParam(name = "hub.verify_token") String verifyToken,
            @RequestParam(name = "hub.challenge") String challenge
    ) {

        return metaEntityUtil.verifyMetaWebhook(mode, verifyToken, challenge);
    }

    @PostMapping("/social/facebook")
    public Mono<Void> handleFacebookEntity(@RequestBody JsonNode requestBody) {
        return entityCollectorService.handleMetaEntity(requestBody);
    }

    @PostMapping("/website")
    public Mono<Void> handleWebsiteEntity(@RequestBody JsonNode requestBody) {

        return entityCollectorService.handleWebsiteEntity(requestBody);
    }
}
