package com.fincity.saas.entity.collector.service;

import reactor.core.publisher.Mono;

public class EntityCollectorService {


    public Mono<String> handleFaceBookEntity() {

        return Mono.just("handeling facebook entity");
    }
}
