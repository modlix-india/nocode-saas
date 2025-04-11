package com.fincity.saas.entity.collector.controller;


import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/entity")
public class EntityCollectorController {

    @PostMapping("/social/facebook")
    public Mono<String> handleFacebookEntity() {

        return Mono.just("Facebook entity handled");
    }

    @PostMapping("/website")
    public Mono<String> handleWebsiteEntity() {

        return Mono.just("Website entity handled");
    }
}
