package com.fincity.saas.commons.mongo.service;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import reactor.core.publisher.Mono;

public abstract class AbstractMongoUpdatableDataService<I extends Serializable, D extends AbstractUpdatableDTO<I, I>, R extends ReactiveCrudRepository<D, I>>
        extends AbstractMongoDataService<I, D, R> {

	protected AbstractMongoUpdatableDataService(Class<D> pojoClass) {
		super(pojoClass);
	}

	public Mono<D> update(D entity) {
		
		System.err.println("In Update....");

		return this.updatableEntity(entity)
		        .flatMap(updateableEntity -> this.getLoggedInUserId()
		                .map(e ->
						{
			                updateableEntity.setUpdatedBy(e);
			                updateableEntity.setUpdatedAt(LocalDateTime.now(ZoneId.of("UTC")));
			                return updateableEntity;
		                })
		                .defaultIfEmpty(updateableEntity)
		                .flatMap(ent -> this.repo.save(ent)));
	}

	protected abstract Mono<D> updatableEntity(D entity);
}
