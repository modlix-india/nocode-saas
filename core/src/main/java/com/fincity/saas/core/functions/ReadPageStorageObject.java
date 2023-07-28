package com.fincity.saas.core.functions;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.core.service.connection.appdata.AppDataService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;

public class ReadPageStorageObject extends AbstractReactiveFunction {

	private static final String EVENT_RESULT = "result";

	private static final String FUNCTION_NAME = "ReadPage";

	private static final String NAME_SPACE = "CoreServices.Storage";

	private static final String STORAGE_NAME = "storageName";

	private static final String FILTER = "filter";

	private static final String PAGE = "page";

	private static final String SIZE = "size";

	private static final String COUNT = "count";

	private AppDataService appDataService;

	private ObjectMapper mapper;

	public ReadPageStorageObject(AppDataService appDataService, ObjectMapper mapper) {

		this.appDataService = appDataService;
		this.mapper = mapper;
	}

	public AppDataService getAppDataService() {
		return appDataService;
	}

	@Override
	public FunctionSignature getSignature() {

		Event event = new Event().setName(Event.OUTPUT)
		        .setParameters(Map.of(EVENT_RESULT, Schema.ofAny(EVENT_RESULT)));

		Event errorEvent = new Event().setName(Event.ERROR)
		        .setParameters(Map.of(EVENT_RESULT, Schema.ofAny(EVENT_RESULT)));

		return new FunctionSignature().setNamespace(NAME_SPACE)
		        .setName(FUNCTION_NAME)
		        .setParameters(Map.of(

		                STORAGE_NAME, Parameter.of(STORAGE_NAME, Schema.ofString(STORAGE_NAME)),

		                FILTER, Parameter.of(FILTER, Schema.ofObject(FILTER)
		                        .setDefaultValue(new JsonObject())),

		                PAGE, Parameter.of(PAGE, Schema.ofInteger(PAGE)
		                        .setDefaultValue(new JsonPrimitive(0))),

		                SIZE, Parameter.of(SIZE, Schema.ofInteger(SIZE)
		                        .setDefaultValue(new JsonPrimitive(20))),

		                COUNT, Parameter.of(COUNT, Schema.ofBoolean(COUNT)
		                        .setDefaultValue(new JsonPrimitive(true)))))

		        .setEvents(Map.of(event.getName(), event, errorEvent.getName(), errorEvent));
	}

	@Override
	protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

		String storageName = context.getArguments()
		        .get(STORAGE_NAME)
		        .getAsString();

		JsonObject filter = context.getArguments()
		        .get(FILTER)
		        .getAsJsonObject();

		Integer page = context.getArguments()
		        .get(PAGE)
		        .getAsInt();

		Integer size = context.getArguments()
		        .get(SIZE)
		        .getAsInt();

		Gson gson = new GsonBuilder().create();

		AbstractCondition absc = null;

		if (filter.size() != 0) {
			absc = this.mapper.convertValue(gson.fromJson(filter, Map.class), AbstractCondition.class);
		}

		Query dsq = new Query().setExcludeFields(false)
		        .setFields(List.of())
		        .setCondition(absc)
		        .setPage(page)
		        .setSize(size)
		        .setCount(true);

		return this.appDataService.readPage(null, null, storageName, dsq)
		        .map(receivedObject -> new FunctionOutput(
		                List.of(EventResult.outputOf(Map.of(EVENT_RESULT, gson.toJsonTree(receivedObject))))));
	}

}
