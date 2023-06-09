package com.fincity.saas.core.controller;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.reactive.ReactiveSchemaUtil;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveFunctionRepository;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveSchemaRepository;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.core.kirun.repository.CoreFunctionRepository;
import com.fincity.saas.core.kirun.repository.CoreSchemaRepository;
import com.fincity.saas.core.service.CoreFunctionService;
import com.fincity.saas.core.service.CoreMessageResourceService;
import com.fincity.saas.core.service.CoreSchemaService;
import com.fincity.saas.core.service.connection.appdata.AppDataService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@RestController
@RequestMapping("api/core/function/")
public class FunctionExecutionController {

	private static final String PATH = "execute/{namespace}/{name}";
	private static final String PATH_FULL_NAME = "execute/{name}";
	private static final String PATH_VARIABLE_NAMESPACE = "namespace";
	private static final String PATH_VARIABLE_NAME = "name";

	private static final Map<SchemaType, java.util.function.Function<String, Number>> CONVERTOR = Map.of(
	        SchemaType.DOUBLE, Double::valueOf, SchemaType.FLOAT, Float::valueOf, SchemaType.LONG, Long::valueOf,
	        SchemaType.INTEGER, Integer::valueOf);

	@Autowired
	private CoreFunctionService functionService;

	@Autowired
	private CoreSchemaService schemaService;

	@Autowired
	private CoreMessageResourceService msgService;

	@Autowired
	private AppDataService appDataService;

	@Autowired
	private ObjectMapper objectMapper;

	private ReactiveHybridRepository<ReactiveFunction> coreFunctionRepository;

	@PostConstruct
	public void init() {
		this.coreFunctionRepository = new ReactiveHybridRepository<>(new KIRunReactiveFunctionRepository(),
		        new CoreFunctionRepository(appDataService, objectMapper));
	}

	@GetMapping(PATH)
	public Mono<ResponseEntity<String>> executeWith(@RequestHeader String appCode, @RequestHeader String clientCode,
	        @PathVariable(PATH_VARIABLE_NAMESPACE) String namespace, @PathVariable(PATH_VARIABLE_NAME) String name,
	        ServerHttpRequest request) {

		return this.execute(namespace, name, appCode, clientCode, null, request);
	}

	@GetMapping(PATH_FULL_NAME)
	public Mono<ResponseEntity<String>> executeWith(@RequestHeader String appCode, @RequestHeader String clientCode,
	        @PathVariable(PATH_VARIABLE_NAME) String fullName, ServerHttpRequest request) {

		Tuple2<String, String> tup = this.splitName(fullName);

		return this.execute(tup.getT1(), tup.getT2(), appCode, clientCode, null, request);
	}

	@PostMapping(PATH)
	public Mono<ResponseEntity<String>> executeWith(@RequestHeader String appCode, @RequestHeader String clientCode,
	        @PathVariable(PATH_VARIABLE_NAMESPACE) String namespace, @PathVariable(PATH_VARIABLE_NAME) String name,
	        @RequestBody String jsonString) {

		JsonObject job = StringUtil.safeIsBlank(jsonString) ? new JsonObject()
		        : new Gson().fromJson(jsonString, JsonObject.class);

		return this.execute(namespace, name, appCode, clientCode, job.entrySet()
		        .stream()
		        .collect(Collectors.toMap(Entry::getKey, Entry::getValue)), null);
	}

	@PostMapping(PATH_FULL_NAME)
	public Mono<ResponseEntity<String>> executeWith(@RequestHeader String appCode, @RequestHeader String clientCode,
	        @PathVariable(PATH_VARIABLE_NAME) String fullName, @RequestBody String jsonString) {

		JsonObject job = StringUtil.safeIsBlank(jsonString) ? new JsonObject()
		        : new Gson().fromJson(jsonString, JsonObject.class);

		Tuple2<String, String> tup = this.splitName(fullName);

		return this.execute(tup.getT1(), tup.getT2(), appCode, clientCode, job.entrySet()
		        .stream()
		        .collect(Collectors.toMap(Entry::getKey, Entry::getValue)), null);
	}

	private Tuple2<String, String> splitName(String fullName) {

		int index = fullName.lastIndexOf('.');
		String name = fullName;
		String namespace = null;
		if (index != -1) {

			namespace = fullName.substring(0, index);
			name = fullName.substring(index + 1);
		}

		return Tuples.of(namespace, name);
	}

	private Mono<ResponseEntity<String>> execute(String namespace, String name, String appCode, String clientCode,
	        Map<String, JsonElement> job, ServerHttpRequest request) {

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> this.functionService.getFunctionRepository(appCode, clientCode)
		                .find(namespace, name),

		        (ca, fun) -> Mono.just(new ReactiveHybridRepository<>(new KIRunReactiveSchemaRepository(),
		                new CoreSchemaRepository(), schemaService.getSchemaRepository(appCode, clientCode))),

		        (ca, fun, schRepo) -> job == null ? getRequestParamsToArguments(fun.getSignature()
		                .getParameters(), request, schRepo) : Mono.just(job),

		        (ca, fun, schRepo, args) ->
				{
			        if (fun instanceof DefinitionFunction df && !StringUtil.safeIsBlank(df.getExecutionAuthorization())
			                && !SecurityContextUtil.hasAuthority(df.getExecutionAuthorization(), ca.getAuthorities())) {
				        return msgService.throwMessage(HttpStatus.FORBIDDEN,
				                AbstractMongoMessageResourceService.FORBIDDEN_EXECUTION);

			        }

			        return fun
			                .execute(
			                        new ReactiveFunctionExecutionParameters(
			                                new ReactiveHybridRepository<>(new KIRunReactiveFunctionRepository(),
			                                        this.coreFunctionRepository,
			                                        functionService.getFunctionRepository(appCode, clientCode)),
			                                schRepo).setArguments(args));

		        },

		        (ca, fun, schRepo, args, output) -> this.extractOutputEvent(output))
		        .switchIfEmpty(this.msgService.throwMessage(HttpStatus.NOT_FOUND,
		                AbstractMongoMessageResourceService.OBJECT_NOT_FOUND, "Function", namespace + "." + name));

	}

	private Mono<ResponseEntity<String>> extractOutputEvent(FunctionOutput e) {
		EventResult er = null;

		while ((er = e.next()) != null) {

			if (!Event.OUTPUT.equals(er.getName()))
				continue;

			Map<String, JsonElement> result = er.getResult();

			if (result == null || result.isEmpty())
				return Mono.just(ResponseEntity.ok()
				        .contentLength(0l)
				        .contentType(MediaType.APPLICATION_JSON)
				        .body(""));

			JsonObject resultObj = new JsonObject();
			for (var eachEntry : result.entrySet())
				resultObj.add(eachEntry.getKey(), eachEntry.getValue());

			return Mono.just((new Gson()).toJson(resultObj))
			        .map(objString -> ResponseEntity.ok()
			                .contentType(MediaType.APPLICATION_JSON)
			                .body(objString));
		}

		return Mono.empty();
	}

	private Mono<Map<String, JsonElement>> getRequestParamsToArguments(Map<String, Parameter> parameters,
	        ServerHttpRequest request, ReactiveRepository<Schema> schemaRepository) {

		MultiValueMap<String, String> queryParams = request.getQueryParams();

		return Flux.fromIterable(parameters.entrySet())
		        .flatMap(e ->
				{

			        List<String> value = queryParams.get(e.getKey());

			        if (value == null || value.isEmpty())
				        return null;

			        Schema schema = e.getValue()
			                .getSchema();

			        if (!StringUtil.safeIsBlank(schema.getRef()))
				        return ReactiveSchemaUtil.getSchemaFromRef(schema, schemaRepository, schema.getRef())
				                .map(sch -> Tuples.of(e, sch));

			        return Mono.just(Tuples.of(e, schema));
		        })
		        .flatMap(tup ->
				{
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
