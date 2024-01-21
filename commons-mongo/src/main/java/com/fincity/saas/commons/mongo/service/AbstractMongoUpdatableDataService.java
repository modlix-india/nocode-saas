package com.fincity.saas.commons.mongo.service;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.commons.mongo.model.AbstractOverridableDTO;

import reactor.core.publisher.Mono;

public abstract class AbstractMongoUpdatableDataService<I extends Serializable, D extends AbstractUpdatableDTO<I, I>, R extends ReactiveCrudRepository<D, I>>
		extends AbstractMongoDataService<I, D, R> {

	protected AbstractMongoUpdatableDataService(Class<D> pojoClass) {
		super(pojoClass);
	}

	public Mono<D> update(D entity) {

		return this.updatableEntity(entity)
				.map(ue -> {

					if (ue instanceof AbstractOverridableDTO<?> ovd
							&& entity instanceof AbstractOverridableDTO<?> evd) {
						ovd.setTitle(evd.getTitle());
						ovd.setDescription(evd.getDescription());
					}

					return ue;
				})
				.flatMap(updateableEntity -> this.getLoggedInUserId()
						.map(e -> {
							updateableEntity.setUpdatedBy(e);
							updateableEntity.setUpdatedAt(LocalDateTime.now(ZoneId.of("UTC")));
							if (entity instanceof AbstractOverridableDTO<?>
									&& updateableEntity instanceof AbstractOverridableDTO<?>) {
								((AbstractOverridableDTO<?>) updateableEntity)
										.setMessage(((AbstractOverridableDTO<?>) entity).getMessage());

							}
							return updateableEntity;
						})
						.defaultIfEmpty(updateableEntity)
						.flatMap(ent -> this.repo.save(ent)));
	}

	protected abstract Mono<D> updatableEntity(D entity);
}
