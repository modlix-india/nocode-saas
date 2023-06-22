package com.fincity.saas.commons.jooq.service;

import java.io.Serializable;

import org.jooq.UpdatableRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.dto.AbstractDTO;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class AbstractJOOQDataService<R extends UpdatableRecord<R>, I extends Serializable, D extends AbstractDTO<I, I>, O extends AbstractDAO<R, I, D>> {

	@Autowired
	protected O dao;

	protected final Logger logger;

	protected AbstractJOOQDataService() {
		this.logger = LoggerFactory.getLogger(this.getClass());
	}

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
