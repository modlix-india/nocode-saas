package com.fincity.saas.commons.mongo.service;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.commons.model.dto.AbstractOverridableDTO;

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
						ovd.setPermission(evd.getPermission());
						ovd.setMessage(evd.getMessage());

						// `blueprint` is copied only when the caller supplied one, which
						// is NOT how title and description behave above. Every
						// updatableEntity copies its own content fields by name and none
						// of them knows about this base-class field, so without a copy
						// here a plan could never be saved at all. But an unconditional
						// copy is worse: the page editor PUTs a whole page on every hand
						// edit, and anything that PUTs from a list row carries no
						// blueprint at all, since the LRO projection omits it. Either
						// would erase the plan while reporting success. So null means
						// "not supplied" and an empty map is how a plan is cleared.
						if (evd.getBlueprint() != null)
							ovd.setBlueprint(evd.getBlueprint());

						// `published` is deliberately NOT copied here. It is server
						// state, not caller state: copying it meant an ordinary PUT
						// that omitted the field nulled it, and a body carrying
						// "published": false hid a live object from the live surface
						// with no draft involved. updatableEntity rebuilds from the
						// stored document, so leaving it alone preserves it, and
						// publish() flips it through its own path.
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
