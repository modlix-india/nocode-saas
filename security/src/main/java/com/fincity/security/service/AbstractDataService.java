package com.fincity.security.service;

import java.io.Serializable;

import org.jooq.UpdatableRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.fincity.security.dao.AbstractDAO;
import com.fincity.security.dto.AbstractDTO;
import com.fincity.security.model.condition.AbstractCondition;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class AbstractDataService<R extends UpdatableRecord<R>, I extends Serializable, D extends AbstractDTO<I, I>, O extends AbstractDAO<R, I, D>> {

	@Autowired
	protected O dao;

	public Mono<D> create(D entity) {

		entity.setCreatedBy(null);
		return this.getLoggedInUserId()
		        .map(e ->
				{
			        entity.setCreatedBy(e);
			        return entity;
		        })
		        .defaultIfEmpty(entity)
		        .flatMap(e -> this.dao.create(e));

	}

	protected Mono<I> getLoggedInUserId() {
		return Mono.empty();
	}

	public Mono<D> read(I id) {
		return this.dao.readById(id);
	}

	public Mono<Page<D>> readPageFilter(Pageable pageable, AbstractCondition condition) {
		return this.dao.readPageFilter(pageable, condition);
	}

	public Flux<D> readAllFilter(AbstractCondition condition) {
		return this.dao.readAll(condition);
	}

	public Mono<Integer> delete(I id) {
		return this.dao.delete(id);
	}
}
