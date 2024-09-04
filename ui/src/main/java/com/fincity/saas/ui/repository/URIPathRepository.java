package com.fincity.saas.ui.repository;

import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;
import com.fincity.saas.ui.document.URIPath;

import reactor.core.publisher.Flux;

public interface URIPathRepository extends IOverridableDataRepository<URIPath> {

	Flux<URIPath> findByAppCodeAndClientCode(String appCode, String clientCode);

}
