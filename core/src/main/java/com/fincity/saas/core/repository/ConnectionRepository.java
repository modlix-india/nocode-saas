package com.fincity.saas.core.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

import com.fincity.saas.core.document.Connection;

import reactor.core.publisher.Mono;

@Repository
public interface ConnectionRepository extends ReactiveCrudRepository<Connection, String> {

	public Mono<Connection> findOneByNameAndAppCodeAndClientCode(String name, String appCode, String clientCode);
}
