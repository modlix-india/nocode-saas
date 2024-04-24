package com.fincity.saas.core.service;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.reactive.ReactiveSchemaUtil;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveFunctionRepository;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveSchemaRepository;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.mongo.service.AbstractFunctionService;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.core.document.CoreFunction;
import com.fincity.saas.core.kirun.repository.CoreFunctionRepository;
import com.fincity.saas.core.kirun.repository.CoreSchemaRepository;
import com.fincity.saas.core.repository.CoreFunctionDocumentRepository;
import com.fincity.saas.core.service.connection.appdata.AppDataService;
import com.fincity.saas.core.service.connection.rest.RestService;
import com.fincity.saas.core.service.security.user.UserContextService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class CoreFunctionService extends AbstractFunctionService<CoreFunction, CoreFunctionDocumentRepository> {

	private ReactiveHybridRepository<ReactiveFunction> coreFunctionRepository;

	private static final Map<SchemaType, java.util.function.Function<String, Number>> CONVERTOR = Map.of(
			SchemaType.DOUBLE, Double::valueOf, SchemaType.FLOAT, Float::valueOf, SchemaType.LONG, Long::valueOf,
			SchemaType.INTEGER, Integer::valueOf);

	@Autowired
	@Lazy
	private AppDataService appDataService;

	@Autowired
	@Lazy
	private RestService restService;

	@Autowired
	@Lazy
	private UserContextService userContextService;

	@Autowired
	@Lazy
	private CoreSchemaService schemaService;

	@PostConstruct
	public void init() {
		this.coreFunctionRepository = new ReactiveHybridRepository<>(new KIRunReactiveFunctionRepository(),
				new CoreFunctionRepository(appDataService, objectMapper, restService, userContextService));
	}

	protected CoreFunctionService() {
		super(CoreFunction.class);
	}

	@Override
	public String getObjectName() {
		return "Function";
	}

	public Mono<FunctionOutput> execute(String namespace, String name, String appCode, String clientCode,
			Map<String, JsonElement> job, ServerHttpRequest request) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.getFunctionRepository(appCode, clientCode)
						.map(appFunctionRepo -> new ReactiveHybridRepository<>(this.coreFunctionRepository,
								appFunctionRepo)),

				(ca, funRepo) -> funRepo.find(namespace, name),

				(ca, funRepo, fun) -> schemaService.getSchemaRepository(appCode, clientCode)
						.map(appSchemaRepo -> new ReactiveHybridRepository<>(new KIRunReactiveSchemaRepository(),
								new CoreSchemaRepository(), appSchemaRepo)),

				(ca, funRepo, fun, schRepo) -> job == null ? getRequestParamsToArguments(fun.getSignature()
						.getParameters(), request, schRepo) : Mono.just(job),

				(ca, funRepo, fun, schRepo, args) -> {
					if (fun instanceof DefinitionFunction df &&
							!StringUtil.safeIsBlank(df.getExecutionAuthorization())
							&& !SecurityContextUtil.hasAuthority(df.getExecutionAuthorization(),
									ca.getAuthorities())) {
						return this.messageResourceService.throwMessage(
								msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
								AbstractMongoMessageResourceService.FORBIDDEN_EXECUTION);

					}

					return fun
							.execute(
									new ReactiveFunctionExecutionParameters(
											new ReactiveHybridRepository<>(new KIRunReactiveFunctionRepository(),
													this.coreFunctionRepository,
													funRepo),
											schRepo).setArguments(args));

				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "CoreFunctionService.execute"));
	}

	private Mono<Map<String, JsonElement>> getRequestParamsToArguments(Map<String, Parameter> parameters,
			ServerHttpRequest request, ReactiveRepository<Schema> schemaRepository) {

		MultiValueMap<String, String> queryParams = request == null ? new LinkedMultiValueMap<>()
				: request.getQueryParams();

		return Flux.fromIterable(parameters.entrySet())
				.flatMap(e -> {

					List<String> value = queryParams.get(e.getKey());

					if (value == null)
						return Mono.empty();

					Schema schema = e.getValue()
							.getSchema();

					if (!StringUtil.safeIsBlank(schema.getRef()))
						return ReactiveSchemaUtil.getSchemaFromRef(schema, schemaRepository, schema.getRef())
								.map(sch -> Tuples.of(e, sch));

					return Mono.just(Tuples.of(e, schema));
				})
				.flatMap(tup -> {
					Entry<String, Parameter> e = tup.getT1();
					Schema schema = tup.getT2();
					Type type = schema.getType();

					Parameter param = e.getValue();
					List<String> value = queryParams.get(e.getKey());

					if (type.contains(SchemaType.ARRAY) || type.contains(SchemaType.OBJECT))
						return Mono.empty();

					if (type.contains(SchemaType.STRING)) {
						return Mono.just(jsonElementString(e, value, param));
					}

					if (type.contains(SchemaType.DOUBLE)) {
						return Mono.just(jsonElement(e, value, param, SchemaType.DOUBLE));
					} else if (type.contains(SchemaType.FLOAT)) {
						return Mono.just(jsonElement(e, value, param, SchemaType.FLOAT));
					} else if (type.contains(SchemaType.LONG)) {
						return Mono.just(jsonElement(e, value, param, SchemaType.LONG));
					} else if (type.contains(SchemaType.INTEGER)) {
						return Mono.just(jsonElement(e, value, param, SchemaType.INTEGER));
					}

					return Mono.empty();
				})
				.filter(Objects::nonNull)
				.collect(Collectors.toMap(Tuple2::getT1, Tuple2::getT2));
	}

	private Tuple2<String, JsonElement> jsonElement(Entry<String, Parameter> e, List<String> value, Parameter param,
			SchemaType type) {

		if (!param.isVariableArgument())
			return Tuples.of(e.getKey(), new JsonPrimitive(CONVERTOR.get(type)
					.apply(value.get(0))));

		JsonArray arr = new JsonArray();
		for (String each : value)
			arr.add(new JsonPrimitive(CONVERTOR.get(type)
					.apply(each)));

		return Tuples.of(e.getKey(), arr);
	}

	private Tuple2<String, JsonElement> jsonElementString(Entry<String, Parameter> e, List<String> value,
			Parameter param) {

		if (!param.isVariableArgument())
			return Tuples.of(e.getKey(), new JsonPrimitive(value.get(0)));

		JsonArray arr = new JsonArray();
		for (String each : value)
			arr.add(new JsonPrimitive(each));

		return Tuples.of(e.getKey(), arr);
	}
}
