package com.fincity.saas.commons.mongo.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.fincity.saas.commons.mongo.document.Draft;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DraftRepository extends ReactiveCrudRepository<Draft, String> {

    Mono<Draft> findOneByObjectAppCodeAndObjectTypeAndObjectNameAndClientCode(String objectAppCode, String objectType,
            String objectName, String clientCode);

    Mono<Draft> findOneByObjectId(String objectId);

    Flux<Draft> findByObjectAppCodeAndClientCode(String objectAppCode, String clientCode);

    Flux<Draft> findByObjectAppCodeAndObjectTypeAndClientCode(String objectAppCode, String objectType,
            String clientCode);

    Mono<Long> deleteByObjectAppCodeAndClientCode(String objectAppCode, String clientCode);

    Mono<Long> deleteByObjectAppCodeAndObjectTypeAndClientCode(String objectAppCode, String objectType,
            String clientCode);

    Mono<Void> deleteByObjectId(String objectId);
}
