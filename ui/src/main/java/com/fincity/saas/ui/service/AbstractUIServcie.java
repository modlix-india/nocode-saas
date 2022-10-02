package com.fincity.saas.ui.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;
import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMonoWithNull;
import static com.fincity.saas.ui.service.UIMessageResourceService.FORBIDDEN_CREATE;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.common.security.jwt.ContextAuthentication;
import com.fincity.saas.common.security.jwt.ContextUser;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.mongo.service.AbstractMongoUpdatableDataService;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.ui.document.AbstractUIDTO;
import com.fincity.saas.ui.document.Version;
import com.fincity.saas.ui.document.Version.ObjectType;
import com.fincity.saas.ui.repository.IUIRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class AbstractUIServcie<D extends AbstractUIDTO<D>, R extends IUIRepository<D>>
        extends AbstractMongoUpdatableDataService<String, D, R> {

	protected static final String CREATE = "CREATE";
	protected static final String UPDATE = "UPDATE";
	protected static final String READ = "READ";
	protected static final String DELETE = "DELETE";

	@Autowired
	protected ObjectMapper objectMapper;

	@Autowired
	protected UIMessageResourceService messageResourceService;

	@Autowired
	protected VersionService versionService;

	@Autowired
	protected IFeignSecurityService securityService;

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

		        ca -> Mono.just((D) entity.setClientCode(ca.getClientCode())),

		        (ca, ent) -> this.checkIfExists(ent),

		        (ca, ent, cent) -> this.accessCheck(ca, CREATE, ent),

		        (ca, ent, cent, hasSecurity) -> hasSecurity.booleanValue() ? Mono.just(cent) : Mono.empty())

		        .switchIfEmpty(messageResourceService.throwMessage(HttpStatus.FORBIDDEN, FORBIDDEN_CREATE,
		                this.pojoClass.getSimpleName()));

		return flatMapMonoWithNull(

		        () -> crtEnt,

		        this::getMergedSources,

		        this::extractOverride,

		        (cEntity, merged, overridden) -> super.create(overridden),

		        (cEntity, merged, overridden,
		                created) -> isVersionable()
		                        ? versionService.create(new Version().setClientCode(cEntity.getClientCode())
		                                .setObjectName(entity.getName())
		                                .setObjectApplicationName(entity.getApplicationName())
		                                .setObjectType(ObjectType.APPLICATION)
		                                .setVersionNumber(1)
		                                .setMessage(entity.getMessage())
		                                .setObject(this.objectMapper.convertValue(entity, TYPE_REFERENCE_MAP)))
		                        : Mono.empty(),

		        (cEntity, merged, overridden, created, version) -> this.read(created.getId()))

		        .switchIfEmpty(messageResourceService.throwMessage(HttpStatus.FORBIDDEN, FORBIDDEN_CREATE,
		                this.pojoClass.getSimpleName()));
	}

	protected Mono<Boolean> accessCheck(ContextAuthentication ca, String method, D entity) {

		return flatMapMono(
		        () -> SecurityContextUtil.hasAuthority("Authorities." + this.pojoClass.getSimpleName() + "_" + method,
		                ca.getAuthorities()) ? Mono.just(true) : Mono.just(false),

		        access -> ca.getClientCode()
		                .equals(entity.getClientCode()) ? Mono.just(true)
		                        : this.securityService.isBeingManaged(ca.getClientCode(), entity.getClientCode()));
	}

	private Mono<D> checkIfExists(D cca) {

		return this.mongoTemplate.count(new Query(new Criteria().andOperator(

		        Criteria.where("name")
		                .is(cca.getName()),
		        Criteria.where("applicationName")
		                .is(cca.getApplicationName()),
		        Criteria.where("clientCode")
		                .is(cca.getClientCode())

		)), this.pojoClass)
		        .flatMap(c -> c > 0
		                ? messageResourceService.throwMessage(HttpStatus.CONFLICT,
		                        UIMessageResourceService.ALREADY_EXISTS, this.pojoClass.getSimpleName(), cca.getName())
		                : Mono.just(cca));
	}

	@Override
	public Mono<D> read(String id) {

		return flatMapMonoWithNull(

		        () -> super.read(id),

		        entity -> SecurityContextUtil.getUsersContextAuthentication(),

		        (entity, ca) -> this.accessCheck(ca, READ, entity),

		        (entity, ca, hasAccess) -> hasAccess.booleanValue() ? this.getMergedSources(entity) : Mono.empty(),

		        (entity, ca, hasAccess, merged) -> hasAccess.booleanValue() ? this.applyOverride(entity, merged)
		                : Mono.empty())

		        .switchIfEmpty(this.messageResourceService.throwMessage(HttpStatus.NOT_FOUND,
		                UIMessageResourceService.OBJECT_NOT_FOUND, this.pojoClass.getSimpleName(), id));
	}

	public Mono<D> readInternal(String id) {

		return flatMapMonoWithNull(

		        () -> super.read(id),

		        this::getMergedSources,

		        this::applyOverride);
	}

	@Override
	public Mono<D> update(D entity) {

		@SuppressWarnings("unchecked")
		Mono<D> crtEnt = flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> Mono.just((D) entity.setClientCode(ca.getClientCode())),

		        (ca, ent) -> this.accessCheck(ca, UPDATE, ent),

		        (ca, ent, hasAccess) -> hasAccess.booleanValue() ? Mono.just(ent) : Mono.empty());

		return crtEnt.flatMap(e -> flatMapMonoWithNull(

		        () -> this.getMergedSources(e),

		        merged -> this.extractOverride(e, merged),

		        (merged, overridden) -> super.update(overridden),

		        (merged, overridden,
		                created) -> isVersionable()
		                        ? versionService.create(new Version().setClientCode(entity.getClientCode())
		                                .setObjectName(entity.getName())
		                                .setObjectApplicationName(entity.getApplicationName())
		                                .setObjectType(ObjectType.APPLICATION)
		                                .setVersionNumber(1)
		                                .setMessage(entity.getMessage())
		                                .setObject(this.objectMapper.convertValue(entity, TYPE_REFERENCE_MAP)))
		                        : Mono.empty(),

		        (merged, overridden, created, version) -> this.read(created.getId())));
	}

	@Override
	public Mono<Boolean> delete(String id) {

		Mono<D> exists = this.repo.findById(id)
		        .switchIfEmpty(messageResourceService.throwMessage(HttpStatus.NOT_FOUND,
		                UIMessageResourceService.OBJECT_NOT_FOUND, this.pojoClass.getSimpleName(), id));

		return flatMapMono(

		        () -> exists,

		        entity -> this.repo.countByNameAndApplicationNameAndBaseClientCode(entity.getName(),
		                entity.getApplicationName(), entity.getClientCode()),

		        (entity, count) -> SecurityContextUtil.getUsersContextAuthentication(),

		        (entity, count, ca) -> this.accessCheck(ca, DELETE, entity),

		        (entity, count, ca, hasAccess) ->
				{

			        if (!hasAccess.booleanValue())
				        return Mono.empty();

			        if (count > 0l)
				        return messageResourceService.throwMessage(HttpStatus.FORBIDDEN,
				                UIMessageResourceService.UNABLE_TO_DELETE, this.pojoClass.getSimpleName(), id);

			        return super.delete(id);
		        }).switchIfEmpty(this.messageResourceService.throwMessage(HttpStatus.NOT_FOUND,
		                UIMessageResourceService.UNABLE_TO_DELETE, this.pojoClass.getSimpleName(), id));
	}

	protected Mono<D> getMergedSources(D entity) {

		if (entity == null)
			return Mono.empty();

		if (entity.getBaseClientCode() == null)
			return Mono.empty();

		Flux<D> x = Mono.just(entity)
		        .expandDeep(e -> e.getBaseClientCode() == null ? Mono.empty()
		                : this.repo.findOneByNameAndApplicationNameAndClientCode(e.getName(), e.getApplicationName(),
		                        e.getBaseClientCode()));

		return x.collectList()
		        .flatMap(list ->
				{
			        if (list.size() == 1)
				        return Mono.empty();

			        if (list.size() == 2)
				        return Mono.just(list.get(1));

			        Mono<D> current = Mono.just(list.get(list.size() - 2));

			        for (int i = list.size() - 3; i >= 0; i--) {
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
		if (entity == null)
			return Mono.empty();
		return entity.makeOverride(mergedSources);
	}

	protected Mono<D> applyOverride(D entity, D mergedSources) {
		if (entity == null)
			return Mono.empty();
		return entity.applyOverride(mergedSources);
	}

	@Override
	protected Mono<String> getLoggedInUserId() {

		return SecurityContextUtil.getUsersContextAuthentication()
		        .map(ContextAuthentication::getUser)
		        .map(ContextUser::getId)
		        .map(Object::toString);
	}
}
