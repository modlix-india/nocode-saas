package com.fincity.saas.ui.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;
import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMonoWithNull;
import static com.fincity.saas.ui.service.UIMessageResourceService.FORBIDDEN_CREATE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.common.security.jwt.ContextAuthentication;
import com.fincity.saas.common.security.jwt.ContextUser;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.mongo.service.AbstractMongoUpdatableDataService;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.ui.document.AbstractUIDTO;
import com.fincity.saas.ui.document.ListResultObject;
import com.fincity.saas.ui.document.Version;
import com.fincity.saas.ui.document.Version.ObjectType;
import com.fincity.saas.ui.repository.IUIRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public abstract class AbstractUIServcie<D extends AbstractUIDTO<D>, R extends IUIRepository<D>>
        extends AbstractMongoUpdatableDataService<String, D, R> {

	private static final String CLIENT_CODE = "clientCode";
	private static final String APP_CODE = "appCode";

	protected static final String CREATE = "CREATE";
	protected static final String UPDATE = "UPDATE";
	protected static final String READ = "READ";
	protected static final String DELETE = "DELETE";

	private static final String CACHE_NAME = "Cache";

	@Autowired
	protected CacheService cacheService;

	@Autowired
	protected ObjectMapper objectMapper;

	@Autowired
	protected UIMessageResourceService messageResourceService;

	@Autowired
	protected VersionService versionService;

	@Autowired
	protected IFeignSecurityService securityService;

	@Autowired
	private InheritanceService inheritanceService;

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
		                                .setObjectAppCode(entity.getAppCode())
		                                .setObjectType(ObjectType.valueOf(this.pojoClass.getSimpleName().toUpperCase()))
		                                .setVersionNumber(1)
		                                .setMessage(entity.getMessage())
		                                .setObject(this.objectMapper.convertValue(entity, TYPE_REFERENCE_MAP)))
		                        : Mono.empty(),

		        (cEntity, merged, overridden, created, version) -> this.read(created.getId()))

		        .switchIfEmpty(messageResourceService.throwMessage(HttpStatus.FORBIDDEN, FORBIDDEN_CREATE,
		                this.pojoClass.getSimpleName()));
	}

	protected Mono<Boolean> accessCheck(ContextAuthentication ca, String method, D entity) {

		if (entity == null)
			return Mono.just(false);

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
		        Criteria.where(APP_CODE)
		                .is(cca.getAppCode()),
		        Criteria.where(CLIENT_CODE)
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
		                                .setObjectAppCode(entity.getAppCode())
		                                .setObjectType(ObjectType.valueOf(this.pojoClass.getSimpleName().toUpperCase()))
		                                .setVersionNumber(1)
		                                .setMessage(entity.getMessage())
		                                .setObject(this.objectMapper.convertValue(entity, TYPE_REFERENCE_MAP)))
		                        : Mono.empty(),

		        (merged, overridden, created, version) -> this.read(created.getId()),

		        (m, o, c, v, f) ->
				{

			        this.evictRecursively(f)
			                .subscribe();

			        return Mono.just(f);
		        }));
	}

	protected Mono<D> evictRecursively(D f) {

		Flux.just(f)
		        .expandDeep(e -> this.repo.findByNameAndAppCodeAndBaseClientCode(e.getName(), e.getAppCode(),
		                e.getClientCode()))
		        .subscribe(e -> cacheService
		                .evict(this.getCacheName(), e.getName(), "-", e.getAppCode(), "-", e.getClientCode())
		                .subscribe());

		return Mono.just(f);
	}

	@Override
	public Mono<Boolean> delete(String id) {

		Mono<D> exists = this.repo.findById(id)
		        .switchIfEmpty(messageResourceService.throwMessage(HttpStatus.NOT_FOUND,
		                UIMessageResourceService.OBJECT_NOT_FOUND, this.pojoClass.getSimpleName(), id));

		return flatMapMono(

		        () -> exists,

		        entity -> this.repo.countByNameAndAppCodeAndBaseClientCode(entity.getName(), entity.getAppCode(),
		                entity.getClientCode()),

		        (entity, count) -> SecurityContextUtil.getUsersContextAuthentication(),

		        (entity, count, ca) -> this.accessCheck(ca, DELETE, entity),

		        (entity, count, ca, hasAccess) ->
				{

			        if (!hasAccess.booleanValue())
				        return Mono.empty();

			        if (count > 0l)
				        return messageResourceService.throwMessage(HttpStatus.FORBIDDEN,
				                UIMessageResourceService.UNABLE_TO_DELETE, this.pojoClass.getSimpleName(), id);

			        cacheService
			                .evict(this.getCacheName(), entity.getName(), "-", entity.getAppCode(), "-",
			                        entity.getClientCode())
			                .subscribe();
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
		                : this.repo.findOneByNameAndAppCodeAndClientCode(e.getName(), e.getAppCode(),
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
		
		if (mergedSources == null)
			return Mono.just(entity);
		
		return entity.makeOverride(mergedSources);
	}

	protected Mono<D> applyOverride(D entity, D mergedSources) {
		if (entity == null)
			return Mono.empty();
		
		if (mergedSources == null)
			return Mono.just(entity);
		
		return entity.applyOverride(mergedSources);
	}

	@Override
	protected Mono<String> getLoggedInUserId() {

		return SecurityContextUtil.getUsersContextAuthentication()
		        .map(ContextAuthentication::getUser)
		        .map(ContextUser::getId)
		        .map(Object::toString);
	}

	public Mono<Page<ListResultObject>> readPageFilterLRO(Pageable pageable, MultiValueMap<String, String> params) {

		final String appName = params.getFirst(APP_CODE) == null ? "" : params.getFirst(APP_CODE);

		Mono<Tuple2<ComplexCondition, List<String>>> condition = paramToConditionLRO(params, appName);

		return condition.flatMap(tup -> FlatMapUtil.flatMapMono(

		        () -> this.filter(tup.getT1()),

		        crit -> this.mongoTemplate
		                .find(new Query(crit).with(pageable.getSort()), ListResultObject.class,
		                        this.pojoClass.getSimpleName()
		                                .toLowerCase())
		                .collectList(),

		        (crit, list) ->
				{
			        Map<String, ListResultObject> things = new HashMap<>();

			        String clientCode = tup.getT2()
			                .isEmpty() ? null
			                        : tup.getT2()
			                                .get(0);

			        for (ListResultObject lro : list) {

				        if (!things.containsKey(lro.getName())) {
					        things.put(lro.getName(), lro);
					        continue;
				        }

				        if (lro.getClientCode()
				                .equals(clientCode)) {
					        things.put(lro.getName(), lro);
				        }
			        }

			        Set<String> ids = things.values()
			                .stream()
			                .map(ListResultObject::getId)
			                .collect(Collectors.toSet());

			        List<ListResultObject> nList = list.stream()
			                .sequential()
			                .filter(e -> ids.contains(e.getId()))
			                .toList();

			        int from = (int) pageable.getOffset();
			        int to = (int) pageable.getOffset() + pageable.getPageSize();

			        if (nList.size() > from && nList.size() >= to)
				        nList = nList.subList(from, to);
			        else
				        nList = List.of();

			        return Mono.just(new PageImpl<>(nList, pageable, nList.size()));
		        }));
	}

	private Mono<Tuple2<ComplexCondition, List<String>>> paramToConditionLRO(MultiValueMap<String, String> params,
	        final String appName) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca ->
				{
			        if (params.containsKey(CLIENT_CODE) && ca.isSystemClient())
				        return this.securityService.isBeingManaged(ca.getUser()
				                .getClientCode(), params.getFirst(CLIENT_CODE));

			        return Mono.just(Boolean.TRUE);
		        },

		        (ca, isBeingManaged) ->
				{

			        if (!isBeingManaged.booleanValue())
				        return Mono.empty();

			        String cc = params.getFirst(CLIENT_CODE);
			        return Mono.just(cc == null ? ca.getUser()
			                .getClientCode() : cc);
		        },

		        (ca, isBeingManaged, finClientCode) -> this.inheritanceService.order(appName, finClientCode),

		        (ca, isBeingManaged, finClientCode, inheritance) ->
				{

			        List<AbstractCondition> conditions = new ArrayList<>();

			        if (inheritance.size() == 1)
				        conditions.add(new FilterCondition().setField(CLIENT_CODE)
				                .setOperator(FilterConditionOperator.EQUALS)
				                .setValue(inheritance.get(0)));
			        else
				        conditions.add(new FilterCondition().setField(CLIENT_CODE)
				                .setOperator(FilterConditionOperator.IN)
				                .setValue(inheritance.stream()
				                        .collect(Collectors.joining(","))));

			        String applicationName = params.getFirst(APP_CODE);
			        conditions.add(new FilterCondition().setField(APP_CODE)
			                .setOperator(FilterConditionOperator.EQUALS)
			                .setValue(applicationName));

			        conditions.addAll(params.entrySet()
			                .stream()
			                .filter(e -> !e.getKey()
			                        .equals(APP_CODE)
			                        && !e.getKey()
			                                .equals(CLIENT_CODE))
			                .filter(e -> Objects.nonNull(e.getValue()))
			                .filter(e -> !e.getValue()
			                        .isEmpty())
			                .map(e -> new FilterCondition().setField(e.getKey())
			                        .setOperator(FilterConditionOperator.STRING_LOOSE_EQUAL)
			                        .setValue(e.getValue()
			                                .get(0)))
			                .toList());

			        Tuple2<ComplexCondition, List<String>> tup = Tuples
			                .of(new ComplexCondition().setConditions(conditions)
			                        .setOperator(ComplexConditionOperator.AND), inheritance);
			        return Mono.just(tup);
		        });
	}

	public Mono<D> read(String name, String appCode, String clientCode) {

		return FlatMapUtil.flatMapMonoWithNull(

		        () -> cacheService.makeKey(name, "-", appCode, "-", clientCode),

		        key -> cacheService.get(this.getCacheName(), key)
		                .map(this.pojoClass::cast),

		        (key, cApp) -> Mono.justOrEmpty(cApp)
		                .switchIfEmpty(Mono
		                        .defer(() -> this.repo.findOneByNameAndAppCodeAndClientCode(name, appCode, clientCode)
		                                .map(this.pojoClass::cast))),

		        (key, cApp, dbApp) -> Mono.justOrEmpty(dbApp)
		                .flatMap(da -> this.readInternal(da.getId())
		                        .map(this.pojoClass::cast)),

		        (key, cApp, dbApp, mergedApp) ->
				{

			        if (cApp == null && mergedApp == null)
				        return Mono.empty();

			        try {
				        return Mono.just(this.pojoClass.getConstructor(this.pojoClass)
				                .newInstance(cApp != null ? cApp : mergedApp));
			        } catch (Exception e) {

				        return this.messageResourceService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR, e,
				                UIMessageResourceService.UNABLE_TO_CREAT_OBJECT, this.pojoClass.getSimpleName());
			        }
		        },

		        (key, cApp, dbApp, mergedApp, clonedApp) ->
				{

			        if (clonedApp == null)
				        return Mono.empty();

			        if (cApp == null && mergedApp != null) {
				        cacheService.put(this.getCacheName(), mergedApp, key);
			        }

			        return this.applyChange(clonedApp);
		        });
	}

	protected Mono<D> applyChange(D object) {
		return Mono.just(object);
	}

	protected String getCacheName() {

		return this.pojoClass.getSimpleName() + CACHE_NAME;
	}
}
