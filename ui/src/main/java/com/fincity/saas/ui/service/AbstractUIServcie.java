package com.fincity.saas.ui.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;
import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMonoWithNull;
import static com.fincity.saas.ui.service.MessageResourceService.FORBIDDEN_CREATE;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.common.security.jwt.ContextAuthentication;
import com.fincity.saas.common.security.jwt.ContextUser;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.mongo.service.AbstractMongoUpdatableDataService;
import com.fincity.saas.ui.document.AbstractUIDTO;
import com.fincity.saas.ui.document.Version;
import com.fincity.saas.ui.document.Version.ObjectType;
import com.fincity.saas.ui.repository.IUIRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class AbstractUIServcie<D extends AbstractUIDTO<D>, R extends IUIRepository<D>>
        extends AbstractMongoUpdatableDataService<String, D, R> {

	@Autowired
	protected ObjectMapper objectMapper;

	@Autowired
	protected MessageResourceService messageResourceService;

	@Autowired
	protected VersionService versionService;

	protected static final TypeReference<Map<String, Object>> TYPE_REFERENCE_MAP = new TypeReference<Map<String, Object>>() {
	};

	protected AbstractUIServcie(Class<D> pojoClass) {
		super(pojoClass);
	}

	@Override
	public Mono<D> create(D entity) {

		@SuppressWarnings("unchecked")
		Mono<D> crtEnt = flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> Mono.just((D) entity.setClientCode(ca.getClientCode())));

		return crtEnt.flatMap(e -> flatMapMonoWithNull(

		        () -> this.getMergedSources(entity),

		        merged -> this.extractOverride(entity, merged),

		        (merged, overridden) -> super.create(overridden),

		        (merged, overridden,
		                created) -> versionService.create(new Version().setClientCode(entity.getClientCode())
		                        .setObjectName(entity.getName())
		                        .setObjectApplicationName(entity.getApplicationName())
		                        .setObjectType(ObjectType.APPLICATION)
		                        .setVersionNumber(1)
		                        .setMessage(entity.getMessage())
		                        .setObject(this.objectMapper.convertValue(entity, TYPE_REFERENCE_MAP))),

		        (merged, overridden, created, version) -> this.read(created.getId())))

		        .switchIfEmpty(messageResourceService.throwMessage(HttpStatus.FORBIDDEN, FORBIDDEN_CREATE,
		                this.pojoClass.getSimpleName()));
	}

	@Override
	public Mono<D> read(String id) {

		return flatMapMono(

		        () -> super.read(id),

		        this::getMergedSources,

		        this::applyOverride);
	}

	@Override
	public Mono<D> update(D entity) {

		@SuppressWarnings("unchecked")
		Mono<D> crtEnt = flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> Mono.just((D) entity.setClientCode(ca.getClientCode())));

		return crtEnt.flatMap(e -> flatMapMonoWithNull(

		        () -> this.getMergedSources(entity),

		        merged -> this.extractOverride(entity, merged),

		        (merged, overridden) -> super.update(overridden),

		        (merged, overridden,
		                created) -> versionService.create(new Version().setClientCode(entity.getClientCode())
		                        .setObjectName(entity.getName())
		                        .setObjectApplicationName(entity.getApplicationName())
		                        .setObjectType(ObjectType.APPLICATION)
		                        .setVersionNumber(1)
		                        .setMessage(entity.getMessage())
		                        .setObject(this.objectMapper.convertValue(entity, TYPE_REFERENCE_MAP))),

		        (merged, overridden, created, version) -> this.read(created.getId())));
	}

	@Override
	public Mono<Void> delete(String id) {

		return flatMapMono(

		        () -> super.read(id),

		        entity -> this.repo.countByNameAndApplicationNameAndBaseClientCode(entity.getName(),
		                entity.getApplicationName(), entity.getClientCode()),

		        (entity, count) ->
				{
			        if (count > 0l)
				        return messageResourceService.throwMessage(HttpStatus.FORBIDDEN,
				                MessageResourceService.UNABLE_TO_DELETE, this.pojoClass.getSimpleName(), id);

			        return super.delete(id);
		        });
	}

	protected Mono<D> getMergedSources(D entity) {

		if (entity.getBaseClientCode() == null)
			return Mono.empty();

		Flux<D> x = Mono.just(entity)
		        .expandDeep(e -> this.repo.findOneByNameAndApplicationNameAndClientCode(e.getName(),
		                e.getApplicationName(), e.getBaseClientCode()));

		return x.collectList()
		        .flatMap(list ->
				{

			        Mono<D> current = Mono.just(list.get(list.size() - 1));

			        for (int i = list.size() - 2; i >= 0; i--) {
				        final int fi = i;
				        current = current.flatMap(b -> list.get(fi)
				                .applyOverride(b));
			        }

			        return current;
		        });
	}

	protected boolean isVersionable() {
		return true;
	}

	protected Mono<D> extractOverride(D entity, D mergedSources) {
		return entity.makeOverride(mergedSources);
	}

	protected Mono<D> applyOverride(D parent, D children) {
		return children.applyOverride(parent);
	}

	@Override
	protected Mono<String> getLoggedInUserId() {

		return SecurityContextUtil.getUsersContextAuthentication()
		        .map(ContextAuthentication::getUser)
		        .map(ContextUser::getId)
		        .map(Object::toString);
	}
}
