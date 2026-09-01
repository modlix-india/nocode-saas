package com.fincity.saas.ui.repository;

import org.springframework.data.mongodb.repository.Aggregation;

import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;
import com.fincity.saas.ui.document.URIPath;

import reactor.core.publisher.Flux;

public interface URIPathRepository extends IOverridableDataRepository<URIPath> {

	/**
	 * Never-published paths are excluded here rather than downstream.
	 *
	 * This is the URI matcher's candidate list, and it does NOT go through
	 * readIfExistsInBase, so the `published` filter there does not cover it. An
	 * unpublished path left in this list matches first, findMatchingURIPath takes
	 * it with .next(), and the subsequent read resolves to nothing: an unpublished
	 * path would shadow a live one into an empty response rather than simply being
	 * invisible.
	 *
	 * `published` is absent on every document that predates the field, so the match
	 * has to treat missing as published.
	 */
	@Aggregation(pipeline = {
			"{ $match: { 'appCode': ?0, 'clientCode': ?1, 'published': { $ne: false } } }",
			"{ $project: { _id: 0, name: 1 } }"
	})
	Flux<String> findAllNamesByAppCodeAndClientCode(String appCode, String clientCode);
}
