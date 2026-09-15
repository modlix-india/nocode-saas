package com.fincity.saas.commons.mongo.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.fincity.saas.commons.mongo.document.Version;
import reactor.core.publisher.Mono;

public interface VersionRepository extends ReactiveCrudRepository<Version, String> {

    public Mono<Long> deleteByObjectAppCodeAndClientCodeAndObjectType(String appCode, String clientCode, String objectType);

    /**
     * One object's content as of one live version.
     *
     * findFirst rather than findOne: nothing constrains versionNumber to be unique
     * per object, and a duplicate would make an ordinary publish throw where the
     * newest record is the obvious answer.
     */
    public Mono<Version> findFirstByObjectTypeAndObjectAppCodeAndObjectNameAndClientCodeAndVersionNumberOrderByCreatedAtDesc(
            String objectType, String objectAppCode, String objectName, String clientCode, int versionNumber);
}
