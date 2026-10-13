package com.fincity.saas.commons.core.repository;

import com.fincity.saas.commons.core.document.EventDefinition;
import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;

import reactor.core.publisher.Flux;

public interface EventDefinitionRepository extends IOverridableDataRepository<EventDefinition> {

    /** Every client's copy of one event in one app: the owner's base and each override, as stored. */
    Flux<EventDefinition> findByNameAndAppCode(String name, String appCode);
}
