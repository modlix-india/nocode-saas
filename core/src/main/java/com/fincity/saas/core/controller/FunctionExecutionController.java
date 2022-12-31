package com.fincity.saas.core.controller;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
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

import com.fincity.nocode.kirun.engine.HybridRepository;
import com.fincity.nocode.kirun.engine.Repository;
import com.fincity.nocode.kirun.engine.function.Function;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.SchemaUtil;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.FunctionExecutionParameters;
import com.fincity.saas.commons.mongo.service.FunctionService;
import com.fincity.saas.commons.mongo.service.SchemaService;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.core.kirun.repository.CoreFunctionRepository;
import com.fincity.saas.core.kirun.repository.CoreSchemaRepository;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
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
	private FunctionService functionService;

	@Autowired
	private SchemaService schemaService;

	@GetMapping(PATH)
	public Mono<ResponseEntity<Object>> executeWith(@RequestHeader String appCode, @RequestHeader String clientCode,
	        @PathVariable(PATH_VARIABLE_NAMESPACE) String namespace, @PathVariable(PATH_VARIABLE_NAME) String name,
	        ServerHttpRequest request) {

		return this.execute(namespace, name, appCode, clientCode, request);
	}

	@GetMapping(PATH_FULL_NAME)
	public Mono<ResponseEntity<Object>> executeWith(@RequestHeader String appCode, @RequestHeader String clientCode,
	        @PathVariable(PATH_VARIABLE_NAME) String fullName, ServerHttpRequest request) {

		Tuple2<String, String> tup = this.splitName(fullName);

		return this.execute(tup.getT1(), tup.getT2(), appCode, clientCode, request);
	}

	@PostMapping(PATH)
	public Mono<ResponseEntity<Object>> executeWith(@RequestHeader String appCode, @RequestHeader String clientCode,
	        @PathVariable(PATH_VARIABLE_NAMESPACE) String namespace, @PathVariable(PATH_VARIABLE_NAME) String name,
	        @RequestBody String jsonString) {

		JsonObject job = StringUtil.safeIsBlank(jsonString) ? new JsonObject()
		        : new Gson().fromJson(jsonString, JsonObject.class);

		return this.execute(namespace, name, appCode, clientCode, job);
	}

	@PostMapping(PATH_FULL_NAME)
	public Mono<ResponseEntity<Object>> executeWith(@RequestHeader String appCode, @RequestHeader String clientCode,
	        @PathVariable(PATH_VARIABLE_NAME) String fullName, @RequestBody String jsonString) {

		JsonObject job = StringUtil.safeIsBlank(jsonString) ? new JsonObject()
		        : new Gson().fromJson(jsonString, JsonObject.class);

		Tuple2<String, String> tup = this.splitName(fullName);

		return this.execute(tup.getT1(), tup.getT2(), appCode, clientCode, job);
	}

	private Tuple2<String, String> splitName(String fullName) {

		int index = fullName.lastIndexOf('.');
		String name = fullName;
		String namespace = null;
		if (index == -1) {

			namespace = fullName.substring(0, index);
			name = fullName.substring(index + 1);
		}

		return Tuples.of(namespace, name);
	}

	private Mono<ResponseEntity<Object>> execute(String namespace, String name, String appCode, String clientCode,
	        ServerHttpRequest request) {

		return Mono.fromCallable(() -> {

			Function fun = this.functionService.getFunctionRepository(appCode, clientCode)
			        .find(namespace, name);

			Repository<Schema> schemaRepository = new HybridRepository<>(new CoreSchemaRepository(),
			        schemaService.getSchemaRepository(appCode, clientCode));

			return fun.execute(new FunctionExecutionParameters(new HybridRepository<>(new CoreFunctionRepository(),
			        functionService.getFunctionRepository(appCode, clientCode)), schemaRepository)
			        .setArguments(this.getRequestParamsToArguments(fun.getSignature()
			                .getParameters(), request, schemaRepository)));
		})
		        .flatMap(this::extractOutpuEvent)
		        .subscribeOn(Schedulers.boundedElastic())
		        .map(ResponseEntity::ok);
	}

	private Mono<ResponseEntity<Object>> execute(String namespace, String name, String appCode, String clientCode,
	        JsonObject job) {

		return Mono.fromCallable(() -> {

			Function fun = this.functionService.getFunctionRepository(appCode, clientCode)
			        .find(namespace, name);

			Repository<Schema> schemaRepository = new HybridRepository<>(new CoreSchemaRepository(),
			        schemaService.getSchemaRepository(appCode, clientCode));

			return fun.execute(new FunctionExecutionParameters(new HybridRepository<>(new CoreFunctionRepository(),
			        functionService.getFunctionRepository(appCode, clientCode)), schemaRepository)
			        .setArguments(job.entrySet()
			                .stream()
			                .collect(Collectors.toMap(Entry::getKey, Entry::getValue))));
		})
		        .flatMap(this::extractOutpuEvent)
		        .subscribeOn(Schedulers.boundedElastic())
		        .map(ResponseEntity::ok);

	}

	private Mono<Map<String, JsonElement>> extractOutpuEvent(FunctionOutput e) {
		EventResult er = null;

		while ((er = e.next()) != null) {

			if (!Event.OUTPUT.equals(er.getName()))
				continue;

			return Mono.just(er.getResult());
		}

		return Mono.empty();
	}

	private Map<String, JsonElement> getRequestParamsToArguments(Map<String, Parameter> parameters,
	        ServerHttpRequest request, Repository<Schema> schemaRepository) {

		MultiValueMap<String, String> queryParams = request.getQueryParams();

		return parameters.entrySet()
		        .stream()
		        .map(e ->
				{

			        List<String> value = queryParams.get(e.getKey());

			        if (value == null || value.isEmpty())
				        return null;

			        Parameter param = e.getValue();

			        Schema schema = param.getSchema();

			        if (!StringUtil.safeIsBlank(schema.getRef()))
				        schema = SchemaUtil.getSchemaFromRef(schema, schemaRepository, schema.getRef());

			        Type type = schema.getType();

			        if (type.contains(SchemaType.ARRAY) || type.contains(SchemaType.OBJECT))
				        return null;

			        if (type.contains(SchemaType.STRING)) {
				        return jsonElementString(e, value, param);
			        }

			        if (type.contains(SchemaType.DOUBLE)) {
				        return jsonElement(e, value, param, SchemaType.DOUBLE);
			        }

			        return null;
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
