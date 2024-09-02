package com.fincity.saas.ui.repository;

import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;
import com.fincity.saas.ui.document.URI;

import reactor.core.publisher.Mono;

public interface URIRepository extends IOverridableDataRepository<URI> {

	Mono<URI> findByPathAndAppCodeAndClientCode(String uriString, String appCode, String clientCode);
}
