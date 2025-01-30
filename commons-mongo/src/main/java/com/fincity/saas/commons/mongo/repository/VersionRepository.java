package com.fincity.saas.commons.mongo.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.fincity.saas.commons.mongo.document.Version;
import reactor.core.publisher.Mono;

public interface VersionRepository extends ReactiveCrudRepository<Version, String> {

    public Mono<Long> deleteByObjectAppCodeAndClientCodeAndObjectType(String appCode, String clientCode, String objectType);
}
