package com.fincity.saas.ui.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.fincity.saas.ui.document.AbstractUIDTO;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface IUIRepository<D extends AbstractUIDTO<D>> extends ReactiveCrudRepository<D, String> {

	public Mono<D> findOneByNameAndAppCodeAndClientCode(String name, String applicationName, String clientCode);

	public Flux<D> findByNameAndAppCodeAndBaseClientCode(String name, String applicationName,
	        String baseClientCode);

	public Mono<Long> countByNameAndAppCodeAndBaseClientCode(String name, String applicationName,
	        String clientCode);
}
