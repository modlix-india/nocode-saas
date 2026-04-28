package com.fincity.security.service;

import java.io.Serializable;
import java.util.Map;

import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.dto.SoxLog;
import com.fincity.security.jooq.enums.SecuritySoxLogActionName;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;

import reactor.core.publisher.Mono;

public abstract class AbstractSecurityUpdatableDataService<R extends UpdatableRecord<R>, I extends Serializable, D extends AbstractUpdatableDTO<I, I>, O extends AbstractUpdatableDAO<R, I, D>>
        extends AbstractJOOQUpdatableDataService<R, I, D, O> {

	@Autowired
	private SoxLogService soxLogService;

	@Autowired
	@Lazy
	private ClientActivityService clientActivityService;

	public abstract SecuritySoxLogObjectName getSoxObjectName();

	protected abstract ULong resolveClientId(D entity);

	protected String describeEntity(D entity) {
		return null;
	}

	private String withIdentifier(String base, D entity) {
		String identifier = describeEntity(entity);
		return (identifier == null || identifier.isBlank()) ? base : base + ": " + identifier;
	}

	@Override
	public Mono<D> create(D entity) {

		return super.create(entity).map(e -> {

			this.dao.getPojoClass()
			        .map(Class::getSimpleName)
			        .flatMap(description -> {
			            String enriched = withIdentifier(description + " created", e);
			            soxLogService.create(new SoxLog().setActionName(SecuritySoxLogActionName.CREATE)
			                    .setObjectName(getSoxObjectName())
			                    .setObjectId(ULongUtil.valueOf(e.getId()))
			                    .setDescription(enriched))
			                    .subscribe();
			            clientActivityService.createLog(resolveClientId(e),
			                    description + " Create", enriched);
			            return Mono.empty();
			        })
			        .subscribe();

			return e;
		});
	}

	@Override
	public Mono<Integer> delete(I id) {

		return this.read(id).flatMap(entity -> super.delete(id).map(e -> {

			ULong clientId = resolveClientId(entity);

			this.dao.getPojoClass()
			        .map(Class::getSimpleName)
			        .flatMap(description -> {
			            String enriched = withIdentifier(description + " deleted", entity);
			            soxLogService.create(new SoxLog().setActionName(SecuritySoxLogActionName.DELETE)
			                    .setObjectName(getSoxObjectName())
			                    .setDescription(enriched)
			                    .setObjectId(ULongUtil.valueOf(id)))
			                    .subscribe();
			            clientActivityService.createLog(clientId,
			                    description + " Delete", enriched);
			            return Mono.empty();
			        })
			        .subscribe();

			return e;
		}));
	}

	@Override
	public Mono<D> update(D entity) {

		return super.update(entity).map(e -> {

			this.dao.getPojoClass()
			        .map(Class::getSimpleName)
			        .flatMap(description -> {
			            String enriched = withIdentifier(description + " updated", e);
			            soxLogService.create(new SoxLog().setActionName(SecuritySoxLogActionName.UPDATE)
			                    .setObjectName(getSoxObjectName())
			                    .setObjectId(ULongUtil.valueOf(e.getId()))
			                    .setDescription(enriched))
			                    .subscribe();
			            clientActivityService.createLog(resolveClientId(e),
			                    description + " Update", enriched);
			            return Mono.empty();
			        })
			        .subscribe();

			return e;
		});
	}

	@Override
	protected Mono<D> updatableEntity(D entity) {
		return Mono.just(entity);
	}

	@Override
	public Mono<D> update(I key, Map<String, Object> fields) {
		return super.update(key, fields).map(e -> {

			this.dao.getPojoClass()
			        .map(Class::getSimpleName)
			        .flatMap(description -> {
			            String enriched = withIdentifier(description + " updated", e);
			            soxLogService.create(new SoxLog().setActionName(SecuritySoxLogActionName.UPDATE)
			                    .setObjectName(getSoxObjectName())
			                    .setObjectId(ULongUtil.valueOf(e.getId()))
			                    .setDescription(enriched))
			                    .subscribe();
			            clientActivityService.createLog(resolveClientId(e),
			                    description + " Update", enriched);
			            return Mono.empty();
			        })
			        .subscribe();

			return e;
		});
	}

	public void assignLog(I id, ULong clientId, String description) {

		soxLogService.create(new SoxLog().setActionName(SecuritySoxLogActionName.ASSIGN)
		        .setObjectName(getSoxObjectName())
		        .setObjectId(ULongUtil.valueOf(id))
		        .setDescription(description))
		        .subscribe();

		clientActivityService.createLog(clientId,
		        getSoxObjectName().name().substring(0, 1)
		                + getSoxObjectName().name().substring(1).toLowerCase() + " Assign",
		        description);
	}

	public void unAssignLog(I id, ULong clientId, String description) {

		soxLogService.create(new SoxLog().setActionName(SecuritySoxLogActionName.UNASSIGN)
		        .setObjectName(getSoxObjectName())
		        .setObjectId(ULongUtil.valueOf(id))
		        .setDescription(description))
		        .subscribe();

		clientActivityService.createLog(clientId,
		        getSoxObjectName().name().substring(0, 1)
		                + getSoxObjectName().name().substring(1).toLowerCase() + " Unassign",
		        description);
	}
}
