package com.fincity.security.service;

import java.io.Serializable;
import java.util.Map;

import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.dto.SoxLog;
import com.fincity.security.jooq.enums.SecuritySoxLogActionName;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;

import reactor.core.publisher.Mono;

public abstract class AbstractSecurityUpdatableDataService<R extends UpdatableRecord<R>, I extends Serializable, D extends AbstractUpdatableDTO<I, I>, O extends AbstractUpdatableDAO<R, I, D>>
        extends AbstractJOOQUpdatableDataService<R, I, D, O> {

	private static final String UPDATED = "updated";

	@Autowired
	private SoxLogService soxService;

	public abstract SecuritySoxLogObjectName getSoxObjectName();

	@Override
	public Mono<D> create(D entity) {

		return super.create(entity).map(e -> {

			this.dao.getPojoClass()
			        .map(Class::getSimpleName) // adding description of the operation coming from mono<class<D>>
			        .flatMap(
			                description -> soxService.create(new SoxLog().setActionName(SecuritySoxLogActionName.CREATE)
			                        .setObjectName(getSoxObjectName())
			                        .setObjectId(ULong.valueOf(e.getId()
			                                .toString()))
			                        .setDescription(description + " created ")))
			        .subscribe();

			return e;
		});
	}

	@Override
	public Mono<Integer> delete(I id) {

		return super.delete(id).map(e -> {

			this.dao.getPojoClass()
			        .map(Class::getSimpleName)
			        .flatMap(
			                description -> soxService.create(new SoxLog().setActionName(SecuritySoxLogActionName.DELETE)
			                        .setObjectName(getSoxObjectName())
			                        .setDescription(description + " deleted ")
			                        .setObjectId(ULong.valueOf(id.toString()))))
			        .subscribe();

			return e;
		});
	}

	@Override
	public Mono<D> update(D entity) {

		return super.update(entity).map(e -> {

			this.dao.getPojoClass()
			        .map(Class::getSimpleName)
			        .flatMap(
			                description -> soxService.create(new SoxLog().setActionName(SecuritySoxLogActionName.UPDATE)
			                        .setObjectName(getSoxObjectName())
			                        .setObjectId(ULong.valueOf(e.getId()
			                                .toString()))
			                        .setDescription(description + UPDATED)))
			        .subscribe();

			return e;
		});
	}

	@Override
	protected Mono<D> updatableEntity(D entity) {
		return super.update(entity).map(e -> {

			this.dao.getPojoClass()
			        .map(Class::getSimpleName)
			        .flatMap(
			                description -> soxService.create(new SoxLog().setActionName(SecuritySoxLogActionName.UPDATE)
			                        .setObjectName(getSoxObjectName())
			                        .setObjectId(ULong.valueOf(e.getId()
			                                .toString()))
			                        .setDescription(description + UPDATED)))
			        .subscribe();

			return e;
		});
	}

	@Override
	public Mono<D> update(I key, Map<String, Object> fields) {
		return super.update(key, fields).map(e -> {

			this.dao.getPojoClass()
			        .map(Class::getSimpleName)
			        .flatMap(
			                description -> soxService.create(new SoxLog().setActionName(SecuritySoxLogActionName.UPDATE)
			                        .setObjectName(getSoxObjectName())
			                        .setObjectId(ULong.valueOf(e.getId()
			                                .toString()))
			                        .setDescription(description + UPDATED)))
			        .subscribe();

			return e;
		});
	}
}
