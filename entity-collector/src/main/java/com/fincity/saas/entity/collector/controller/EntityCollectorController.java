package com.fincity.saas.entity.collector.controller;


import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.saas.entity.collector.service.EntityCollectorService;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;


@RestController
@RequestMapping("/api/entity")
@AllArgsConstructor
public class EntityCollectorController {

    public final EntityCollectorService entityCollectorService;

    @PostMapping("/social/facebook")
    public Mono<JsonNode> handleFacebookEntity(@RequestBody JsonNode requestBody) {

        return entityCollectorService.handleMetaEntity(requestBody);
    }

    @PostMapping("/website")
    public Mono<JsonNode> handleWebsiteEntity(@RequestBody JsonNode requestBody) {

        return entityCollectorService.handleWebsiteEntity(requestBody);
    }

}
