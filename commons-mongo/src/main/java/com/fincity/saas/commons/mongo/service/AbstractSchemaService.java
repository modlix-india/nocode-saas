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
	 * A schema's {@code definition} is raw KIRun Schema JSON, and a reference
	 * is a plain {@code ref} string, never {@code $ref} - {@code $defs} is the
	 * only dollar prefixed key KIRun has. Refs nest wherever a sub schema is
	 * allowed: {@code properties}, {@code patternProperties},
	 * {@code propertyNames}, {@code anyOf}/{@code allOf}/{@code oneOf},
	 * {@code not}, {@code contains}, {@code $defs}, {@code items},
	 * {@code additionalProperties} and {@code additionalItems}.
	 * <p>
	 * The walk is deliberately blind rather than field by field. {@code items}
	 * and the two additional* fields are Gson union types whose adapters accept
	 * a bare schema, a wrapped {@code singleSchema}/{@code tupleSchema}/
	 * {@code schemaValue}, a raw array or a bare boolean, and only ever write
	 * the bare form back - so stored definitions carry a mix of shapes. A
	 * recursive walk keyed on {@code ref} is immune to all of that; a typed
	 * reader would have to reimplement both adapters.
	 */
	@Override
	public Collection<String> getTransportDependencies(D entity) {

		if (entity == null || entity.getDefinition() == null)
			return List.of();

		Set<String> refs = new LinkedHashSet<>();
		collectRefs(entity.getDefinition(), refs);
		return refs;
	}

	private static void collectRefs(Object node, Set<String> refs) {

		if (node instanceof Map<?, ?> map) {
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				if (REF.equals(entry.getKey()) && entry.getValue() instanceof String ref)
					addSchemaNames(ref, refs);
				else
					collectRefs(entry.getValue(), refs);
			}
		} else if (node instanceof Iterable<?> iterable) {
			for (Object value : iterable)
				collectRefs(value, refs);
		}
	}

	/**
	 * Turns a ref string into the schema document names it could point at.
	 * <p>
	 * A schema document is stored under {@code namespace + "." + name}, and
	 * ReactiveSchemaUtil resolves an external ref by cutting at the first
	 * {@code /} and splitting the head at its <b>last</b> dot - so the head is
	 * already exactly the document name. The dot trimmed prefixes are emitted
	 * too because refs written as {@code Model.UserSegment._id} exist in the
	 * wild; only the prefix that names a real schema will match anything.
	 * Refs starting with {@code #} are internal to the schema and are not
	 * edges at all.
	 */
	private static void addSchemaNames(String ref, Set<String> refs) {

		if (StringUtil.safeIsBlank(ref) || ref.charAt(0) == '#')
			return;

		int slash = ref.indexOf('/');
		String name = slash < 0 ? ref : ref.substring(0, slash);

		// A document name is namespace + "." + name, so it always has a dot.
		// Stop once there is none left, which is the bare namespace.
		while (name.indexOf('.') >= 0) {
			refs.add(name);
			name = name.substring(0, name.lastIndexOf('.'));
		}
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
