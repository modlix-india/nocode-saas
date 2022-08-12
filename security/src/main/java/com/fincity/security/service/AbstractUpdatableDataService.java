package com.fincity.security.service;

import java.io.Serializable;
import java.util.Map;

import org.jooq.UpdatableRecord;

import com.fincity.security.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.AbstractUpdatableDTO;

import reactor.core.publisher.Mono;

public abstract class AbstractUpdatableDataService<R extends UpdatableRecord<R>, I extends Serializable, D extends AbstractUpdatableDTO<I, I>, O extends AbstractUpdatableDAO<R, I, D>>
        extends AbstractDataService<R, I, D, O> {

	public Mono<D> update(I key, Map<String, Object> updateFields) {

		updateFields.put("updatedBy", this.getLoggedInUserId());

		return this.dao.update(key, updateFields);
	}

	public Mono<D> update(D entity) {
		entity.setUpdatedBy(this.getLoggedInUserId());
		return this.dao.update(entity);
	}
}
