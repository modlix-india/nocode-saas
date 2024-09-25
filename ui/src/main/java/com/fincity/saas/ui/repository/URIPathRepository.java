package com.fincity.saas.ui.repository;

import org.springframework.data.mongodb.repository.Aggregation;

import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;
import com.fincity.saas.ui.document.URIPath;

import reactor.core.publisher.Flux;

public interface URIPathRepository extends IOverridableDataRepository<URIPath> {

	@Aggregation(pipeline = {
			"{ $match: { 'appCode': ?0, 'clientCode': ?1 } }",
			"{ $project: { _id: 0, name: 1 } }"
	})
	Flux<String> findAllNamesByAppCodeAndClientCode(String appCode, String clientCode);
}
