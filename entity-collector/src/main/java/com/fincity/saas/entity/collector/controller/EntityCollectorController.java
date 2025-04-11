package com.fincity.saas.entity.collector.controller;


import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.saas.entity.collector.dto.EntityIntegration;
import com.fincity.saas.entity.collector.service.EntityCollectorService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/entity")
@AllArgsConstructor
public class EntityCollectorController {

    public final EntityCollectorService entityCollectorService;

    @PostMapping("/social/facebook")
    public Mono<List<EntityIntegration>> handleFacebookEntity(@RequestBody JsonNode requestBody) {

        return entityCollectorService.handleFaceBookEntity(requestBody);
    }

    @PostMapping("/website")
    public Mono<String> handleWebsiteEntity() {

        return Mono.just("Website entity handled");
    }
}
