package com.fincity.saas.commons.mongo.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.array.ArraySchemaType;
import com.fincity.nocode.kirun.engine.json.schema.array.ArraySchemaType.ArraySchemaTypeAdapter;
import com.fincity.nocode.kirun.engine.json.schema.object.AdditionalType;
import com.fincity.nocode.kirun.engine.json.schema.object.AdditionalType.AdditionalTypeAdapter;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.nocode.kirun.engine.json.schema.type.Type.SchemaTypeAdapter;
import com.fincity.nocode.kirun.engine.model.FunctionDefinition;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.gson.LocalDateTimeAdapter;
import com.fincity.saas.commons.mongo.document.AbstractFunction;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public abstract class AbstractFunctionService<D extends AbstractFunction<D>, R extends IOverridableDataRepository<D>>
		extends AbstractOverridableDataService<D, R> {

	protected AbstractFunctionService(Class<D> pojoClass) {
		super(pojoClass);
	}

	private static final String NAMESPACE = "namespace";
	private static final String NAME = "name";

	private Map<String, ReactiveRepository<ReactiveFunction>> functions = new HashMap<>();

	@Override
	public Mono<D> create(D entity) {

		String name = StringUtil.safeValueOf(entity.getDefinition()
				.get(NAME));
		String namespace = StringUtil.safeValueOf(entity.getDefinition()
				.get(NAMESPACE));

		if (name == null || namespace == null) {

			return this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST,
					msg),
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

						return this.messageResourceService
								.throwMessage(msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
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

					String funName = namespace + "." + name;

					if (!funName.equals(existing.getName())) {

						return this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
								AbstractMongoMessageResourceService.NAME_CHANGE);
					}

					existing.setExecuteAuth(entity.getExecuteAuth());
					existing.setDefinition(entity.getDefinition());
					existing.setVersion(existing.getVersion() + 1)
							.setPermission(entity.getPermission());

					return Mono.just(existing);
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFunctionService.updatableEntity"));
	}

	static class NameOnly {
		String name;
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

	public ReactiveRepository<ReactiveFunction> getFunctionRepository(String appCode, String clientCode) {

		return functions.computeIfAbsent(appCode + " - " + clientCode,

				key ->

				new ReactiveRepository<ReactiveFunction>() {

					public Mono<ReactiveFunction> find(String namespace, String name) {

						String fnName = StringUtil.safeIsBlank(namespace) ? name : namespace + "." + name;

						return FlatMapUtil.flatMapMono(

								() -> read(fnName, appCode, clientCode),

								s -> {

									ArraySchemaTypeAdapter arraySchemaTypeAdapter = new ArraySchemaTypeAdapter();

									AdditionalTypeAdapter additionalTypeAdapter = new AdditionalTypeAdapter();

									Gson gson = new GsonBuilder()
											.registerTypeAdapter(Type.class, new SchemaTypeAdapter())
											.registerTypeAdapter(AdditionalType.class, additionalTypeAdapter)
											.registerTypeAdapter(ArraySchemaType.class, arraySchemaTypeAdapter)
											.registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
											.create();

									arraySchemaTypeAdapter.setGson(gson);

									additionalTypeAdapter.setGson(gson);

									FunctionDefinition fd = gson.fromJson(
											gson.toJsonTree(s.getObject().getDefinition()),
											FunctionDefinition.class);

									return Mono.just((ReactiveFunction) new DefinitionFunction(fd,
											s.getObject().getExecuteAuth()));
								})
								.contextWrite(Context.of(LogUtil.METHOD_NAME,
										"AbstractFunctionService.getFunctionRepository"));

					}

					public Flux<String> filter(String name) {

						return filterInRepo(appCode, clientCode, name);
					}
				});
	}
}
