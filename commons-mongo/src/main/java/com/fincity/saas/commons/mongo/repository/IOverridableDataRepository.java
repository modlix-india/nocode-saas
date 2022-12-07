package com.fincity.saas.commons.mongo.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface IOverridableDataRepository<D extends AbstractOverridableDTO<D>> extends ReactiveCrudRepository<D, String> {

	public Mono<D> findOneByNameAndAppCodeAndClientCode(String name, String applicationName, String clientCode);

	public Flux<D> findByNameAndAppCodeAndBaseClientCode(String name, String applicationName,
	        String baseClientCode);

	public Mono<Long> countByNameAndAppCodeAndBaseClientCode(String name, String applicationName,
	        String clientCode);
}
