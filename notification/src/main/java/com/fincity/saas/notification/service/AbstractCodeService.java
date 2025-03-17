package com.fincity.saas.notification.service;

import java.io.Serializable;

import org.jooq.UpdatableRecord;

import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.notification.dao.AbstractCodeDao;

import reactor.core.publisher.Mono;

public abstract class AbstractCodeService<R extends UpdatableRecord<R>, I extends Serializable,
		D extends AbstractUpdatableDTO<I, I>, O extends AbstractCodeDao<R, I, D>>
		extends AbstractJOOQUpdatableDataService<R, I, D, O> {

	public Mono<D> getByCode(String code) {
		return this.dao.getByCode(code);
	}
}
