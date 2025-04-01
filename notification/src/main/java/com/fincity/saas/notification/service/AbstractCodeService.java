package com.fincity.saas.notification.service;

import java.io.Serializable;

import org.jooq.UpdatableRecord;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.notification.dao.AbstractCodeDao;

import reactor.core.publisher.Mono;

public abstract class AbstractCodeService<R extends UpdatableRecord<R>, I extends Serializable, D extends AbstractUpdatableDTO<I, I>, O extends AbstractCodeDao<R, I, D>>
		extends AbstractJOOQUpdatableDataService<R, I, D, O> {

	protected abstract String getCacheName();

	protected abstract CacheService getCacheService();

	public Mono<D> getByCode(String code) {
		return this.getCacheService().cacheValueOrGet(this.getCacheName(), () -> this.dao.getByCode(code), code);
	}

	public Mono<D> updateByCode(String code, D entity) {

		return FlatMapUtil.flatMapMono(

				() -> this.getByCode(code).defaultIfEmpty(entity),

				this::updatableEntity,

				(e, updatableEntity) -> this.getLoggedInUserId().map(lEntity -> {
					updatableEntity.setUpdatedBy(lEntity);
					return updatableEntity;
				}).defaultIfEmpty(updatableEntity),

				(e, updatableEntity, uEntity) -> this.dao.update(uEntity),

				(e, updatableEntity, uEntity, updated) -> this.evictCode(code).map(evicted -> updated));
	}

	public Mono<Boolean> evictCode(String code) {
		return this.getCacheService().evict(this.getCacheName(), code);
	}
}
