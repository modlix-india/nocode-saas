package com.fincity.saas.commons.mongo.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.array.ArraySchemaType;
import com.fincity.nocode.kirun.engine.json.schema.array.ArraySchemaType.ArraySchemaTypeAdapter;
import com.fincity.nocode.kirun.engine.json.schema.object.AdditionalType;
import com.fincity.nocode.kirun.engine.json.schema.object.AdditionalType.AdditionalTypeAdapter;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.nocode.kirun.engine.json.schema.type.Type.SchemaTypeAdapter;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.gson.LocalDateTimeAdapter;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mongo.document.AbstractSchema;
import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;
import com.fincity.saas.commons.mongo.service.AbstractFunctionService.NameOnly;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public abstract class AbstractSchemaService<D extends AbstractSchema<D>, R extends IOverridableDataRepository<D>>
		extends AbstractOverridableDataService<D, R> {

	private static final String NAMESPACE = "namespace";
	private static final String NAME = "name";

	private Map<String, ReactiveRepository<com.fincity.nocode.kirun.engine.json.schema.Schema>> schemas = new HashMap<>();

	private Gson gson;

	@Autowired
	private FeignAuthenticationService feignSecurityService;

	protected AbstractSchemaService(Class<D> pojoClass) {
		super(pojoClass);
		AdditionalTypeAdapter at = new AdditionalTypeAdapter();
		ArraySchemaTypeAdapter ast = new ArraySchemaTypeAdapter();
		gson = new GsonBuilder().registerTypeAdapter(Type.class, new SchemaTypeAdapter())
				.registerTypeAdapter(AdditionalType.class, at)
				.registerTypeAdapter(ArraySchemaType.class, ast)
				.registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
				.create();
		at.setGson(this.gson);
		ast.setGson(this.gson);
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
						.map(s -> {

							return gson.fromJson(gson.toJsonTree(s.getDefinition()), Schema.class);
						});
			}

			@Override
			public Flux<String> filter(String name) {

				return filterInRepo(appCode, clientCode, name);
			}

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
