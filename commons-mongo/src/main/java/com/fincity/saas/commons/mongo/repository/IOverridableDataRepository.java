package com.fincity.saas.commons.mongo.repository;

import java.util.List;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface IOverridableDataRepository<D extends AbstractOverridableDTO<D>>
    extends ReactiveCrudRepository<D, String> {

    public Mono<D> findOneByNameAndAppCodeAndClientCode(String name, String appCode, String clientCode);

    public Flux<D> findByNameAndAppCodeAndClientCodeIn(String name, String appCode, List<String> clientCodes);

    public Flux<D> findByNameAndAppCodeAndBaseClientCode(String name, String appCode, String baseClientCode);

    public Mono<Long> countByNameAndAppCodeAndBaseClientCode(String name, String appCode, String clientCode);

    public Flux<D> findByAppCodeAndClientCode(String appCode, String clientCode);

    public Mono<Long> deleteByAppCodeAndClientCode(String appCode, String clientCode);
}
