package com.fincity.saas.ui.repository;

import org.springframework.stereotype.Repository;

import com.fincity.saas.ui.document.Personalization;

import reactor.core.publisher.Mono;

@Repository
public interface PersonalizationRepository extends IUIRepository<Personalization> {

	public Mono<Personalization> findOneByNameAndApplicationNameAndCreatedBy(String name, String applicationName,
	        String createdBy);

	public Mono<Long> deleteByNameAndApplicationNameAndCreatedBy(String name, String applicationName, String createdBy);
}
