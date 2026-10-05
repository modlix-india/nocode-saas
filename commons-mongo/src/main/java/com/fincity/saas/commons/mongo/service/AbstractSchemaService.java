package com.fincity.saas.commons.mongo.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mongo.document.AbstractSchema;
import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;
import com.fincity.saas.commons.mongo.service.AbstractFunctionService.NameOnly;
import com.fincity.saas.commons.mongo.util.SchemaRefs;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.google.gson.Gson;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public abstract class AbstractSchemaService<D extends AbstractSchema<D>, R extends IOverridableDataRepository<D>>
		extends AbstractOverridableDataService<D, R> {

	private static final String NAMESPACE = "namespace";
	private static final String NAME = "name";
	private static final String REF = "ref";

	private final Map<String, ReactiveRepository<com.fincity.nocode.kirun.engine.json.schema.Schema>> schemas = new HashMap<>();

	private final FeignAuthenticationService feignSecurityService;
	protected final Gson gson;

	protected AbstractSchemaService(Class<D> pojoClass, FeignAuthenticationService feignAuthenticationService,
			Gson gson) {
		super(pojoClass);

		this.feignSecurityService = feignAuthenticationService;
		this.gson = gson;
	}

	@Override
	public Mono<D> create(D entity) {

		String name = StringUtil.safeValueOf(entity.getDefinition()
				.get(NAME));
		String namespace = StringUtil.safeValueOf(entity.getDefinition()
				.get(NAMESPACE));

		if (name == null || namespace == null) {
			return this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					AbstractMongoMessageResourceService.NAME_MISSING);
		}

		entity.setName(namespace + "." + name);

		return super.create(entity);
	}

	@Override
	protected Mono<D> updatableEntity(D entity) {

		return flatMapMono(

				() -> this.read(entity.getId()),

				existing -> {
					if (existing.getVersion() != entity.getVersion())
						return this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
								AbstractMongoMessageResourceService.VERSION_MISMATCH);

					String name = StringUtil.safeValueOf(entity.getDefinition()
							.get(NAME));
					String namespace = StringUtil.safeValueOf(entity.getDefinition()
							.get(NAMESPACE));

					if (name == null || namespace == null) {
						return this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
								AbstractMongoMessageResourceService.NAME_MISSING);
					}

					String schemaName = namespace + "." + name;

					if (!schemaName.equals(existing.getName())) {

						return this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
								AbstractMongoMessageResourceService.NAME_CHANGE);
					}

					existing.setDefinition(entity.getDefinition());

					existing.setVersion(existing.getVersion() + 1)
							.setPermission(entity.getPermission());

					return Mono.just(existing);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractSchemaService.updatableEntity"));
	}

	public Mono<ReactiveRepository<Schema>> getSchemaRepository(String appCode, String clientCode) {

		ReactiveRepository<Schema> appRepo = findSchemaRepository(appCode, clientCode);

		return this.feignSecurityService.getDependencies(appCode)
				.map(lst -> {

					if (lst.isEmpty())
						return appRepo;

					@SuppressWarnings("unchecked")
					ReactiveRepository<Schema>[] repos = new ReactiveRepository[lst.size() + 1];
					repos[0] = appRepo;

					for (int i = 0; i < lst.size(); i++) {
						repos[i + 1] = findSchemaRepository(lst.get(i), clientCode);
					}

					return new ReactiveHybridRepository<>(repos);
				})
				.defaultIfEmpty(appRepo);
	}

	private ReactiveRepository<Schema> findSchemaRepository(String appCode, String clientCode) {
		return schemas.computeIfAbsent(appCode + " - " + clientCode, key -> new ReactiveRepository<Schema>() {

			@Override
			public Mono<Schema> find(String namespace, String name) {

				return read(namespace + "." + name, appCode, clientCode)
						.map(ObjectWithUniqueID::getObject)
						.map(s -> gson.fromJson(gson.toJsonTree(s.getDefinition()), Schema.class));
			}

			@Override
			public Flux<String> filter(String name) {

				return filterInRepo(appCode, clientCode, name);
			}

		});
	}

	/**
	 * Every other schema in this app that this one refers to.
	 * <p>
	 * The walk itself lives in {@link SchemaRefs}, because storages hold the
	 * same kind of definition and have to be walked the same way: a storage
	 * whose table is not rebuilt when the schema it points at changes fails
	 * silently, on the first write that hits a column of the wrong type.
	 */
	@Override
	public Collection<String> getTransportDependencies(D entity) {

		if (entity == null || entity.getDefinition() == null)
			return List.of();

		return SchemaRefs.collect(entity.getDefinition());
	}

	/**
	 * Every schema in this app that would change if the named one changed,
	 * including the named one itself.
	 *
	 * Reverse reachability, not forward: the question is not what this schema
	 * points at but what points at it. A storage may reference {@code App.Order},
	 * which references {@code App.Money}, so editing {@code App.Money} changes the
	 * table behind that storage even though nothing about the storage or
	 * {@code App.Order} was touched. Following only the direct references would
	 * find nothing and rebuild nothing.
	 * <p>
	 * Every client's copy is walked, not just the editing client's. A derived
	 * document stores only its difference from its base, so a reference added by
	 * an override exists in that document alone, and reading the base would miss it.
	 */
	public Mono<Set<String>> referencingClosure(String appCode, String schemaName) {

		if (StringUtil.safeIsBlank(appCode) || StringUtil.safeIsBlank(schemaName))
			return Mono.just(Set.of());

		return this.mongoTemplate
				.find(new Query(Criteria.where("appCode")
						.is(appCode)), this.pojoClass, this.getCollectionName())
				.collectList()
				.map(all -> {

					Map<String, Set<String>> refsByDocument = new HashMap<>();

					for (D doc : all)
						if (doc.getName() != null)
							refsByDocument.put(doc.getName(), SchemaRefs.collect(doc.getDefinition()));

					return SchemaRefs.referencingClosure(refsByDocument, schemaName);
				});
	}

	public Flux<String> filterInRepo(String appCode, String clientCode, String filter) {

		Flux<NameOnly> names = this.inheritanceService.order(appCode, clientCode, clientCode)
				.flatMapMany(ccs -> {
					List<Criteria> criteria = new ArrayList<>();

					criteria.add(Criteria.where("appCode")
							.is(appCode));
					criteria.add(Criteria.where("clientCode")
							.in(ccs));

					if (!StringUtil.safeIsBlank(filter))
						criteria.add(Criteria.where("name")
								.regex(Pattern.compile(filter, Pattern.CASE_INSENSITIVE)));

					return this.mongoTemplate.find(new Query(new Criteria().andOperator(criteria)), NameOnly.class,
							this.getCollectionName());
				});

		return names.map(e -> e.name);
	}
}
