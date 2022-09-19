package com.fincity.saas.ui.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.fincity.saas.ui.document.AbstractUIDTO;

import reactor.core.publisher.Mono;

public interface IUIRepository<D extends AbstractUIDTO<D>> extends ReactiveCrudRepository<D, String> {

	public Mono<D> findOneByNameAndApplicationNameAndClientCode(String name, String applicationName, String clientCode);

	public Mono<Long> countByNameAndApplicationNameAndBaseClientCode(String name, String applicationName, String clientCode);
}
