package com.fincity.saas.core.functions.storage;

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
import com.fincity.nocode.kirun.engine.util.string.StringUtil;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.core.service.connection.appdata.AppDataService;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;

public class DeleteStorageObjectWithFilter extends AbstractReactiveFunction {

	private static final String EVENT_RESULT = "result";

	private static final String FUNCTION_NAME = "DeleteByFilter";

	private static final String NAME_SPACE = "CoreServices.Storage";

	private static final String STORAGE_NAME = "storageName";

	private static final String FILTER = "filter";

	private static final String DEV_MODE = "devMode";

	private static final String APP_CODE = "appCode";

	private static final String CLIENT_CODE = "clientCode";

	private final AppDataService appDataService;
	private final ObjectMapper mapper;
	private final Gson gson;

	public DeleteStorageObjectWithFilter(AppDataService appDataService, ObjectMapper mapper, Gson gson) {
		
		this.appDataService = appDataService;
		this.mapper = mapper;
		this.gson = gson;
	}

	@Override
	public FunctionSignature getSignature() {

		Event event = new Event().setName(Event.OUTPUT).setParameters(Map.of(EVENT_RESULT, Schema.ofInteger(EVENT_RESULT)));

		Event errorEvent = new Event().setName(Event.ERROR)
				.setParameters(Map.of(EVENT_RESULT, Schema.ofAny(EVENT_RESULT)));

		return new FunctionSignature().setNamespace(NAME_SPACE).setName(FUNCTION_NAME)

				.setParameters(Map.of(

						STORAGE_NAME, Parameter.of(STORAGE_NAME, Schema.ofString(STORAGE_NAME)),

						FILTER, Parameter.of(FILTER, Schema.ofObject(FILTER).setDefaultValue(new JsonObject())),

						DEV_MODE,
						Parameter.of(DEV_MODE, Schema.ofBoolean(DEV_MODE).setDefaultValue(new JsonPrimitive(false))),

						APP_CODE,
						Parameter.of(APP_CODE, Schema.ofString(APP_CODE).setDefaultValue(new JsonPrimitive(""))),

						CLIENT_CODE,
						Parameter.of(CLIENT_CODE, Schema.ofString(CLIENT_CODE).setDefaultValue(new JsonPrimitive("")))))
						
				.setEvents(Map.of(event.getName(), event, errorEvent.getName(), errorEvent));
	}

	@Override
	protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

		String storageName = context.getArguments().get(STORAGE_NAME).getAsString();

		JsonObject filter = context.getArguments().get(FILTER).getAsJsonObject();

		JsonElement appCodeJSON = context.getArguments().get(APP_CODE);

		String appCode = appCodeJSON == null || appCodeJSON.isJsonNull() ? null : appCodeJSON.getAsString();

		JsonElement clientCodeJSON = context.getArguments().get(CLIENT_CODE);

		String clientCode = clientCodeJSON == null || clientCodeJSON.isJsonNull() ? null : clientCodeJSON.getAsString();

		boolean devMode = context.getArguments().get(DEV_MODE).getAsBoolean();

		AbstractCondition absc = null;

		if (filter.size() != 0) {
			absc = this.mapper.convertValue(gson.fromJson(filter, Map.class), AbstractCondition.class);
		}

		Query dsq = new Query().setExcludeFields(false).setFields(List.of()).setCondition(absc);

		return this.appDataService.deleteByFilter(StringUtil.isNullOrBlank(appCode) ? null : appCode,

				StringUtil.isNullOrBlank(clientCode) ? null : clientCode, storageName, dsq, devMode)
				.map(deletedCount -> {

					Map<String, Object> result = Map.of("deletedCount", deletedCount);

					return new FunctionOutput(
							List.of(EventResult.outputOf(Map.of(EVENT_RESULT, gson.toJsonTree(result)))));
				});
	}
}