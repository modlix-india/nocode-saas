package com.fincity.saas.core.functions;

import java.util.List;
import java.util.Map;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.nocode.kirun.engine.util.string.StringUtil;
import com.fincity.saas.core.model.DataObject;
import com.fincity.saas.core.service.connection.appdata.AppDataService;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;

import reactor.core.publisher.Mono;

public class CreateStorageObject extends AbstractReactiveFunction {

	private static final String DATA_OBJECT = "dataObject";

	private static final String EVENT_RESULT = "result";

	private static final String FUNCTION_NAME = "Create";

	private static final String NAME_SPACE = "CoreServices.Storage";

	private static final String STORAGE_NAME = "storageName";

	private static final String APP_CODE = "appCode";

	private static final String CLIENT_CODE = "clientCode";

	private AppDataService appDataService;

	public CreateStorageObject(AppDataService appDataService) {
		this.appDataService = appDataService;
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

		return new FunctionSignature().setName(FUNCTION_NAME)
				.setNamespace(NAME_SPACE)
				.setParameters(Map.of(STORAGE_NAME, new Parameter().setParameterName(STORAGE_NAME)
						.setSchema(Schema.ofString(STORAGE_NAME)), DATA_OBJECT,
						new Parameter().setParameterName(DATA_OBJECT)
								.setSchema(Schema.ofObject(DATA_OBJECT)),

						APP_CODE,
						Parameter.of(APP_CODE,
								Schema.ofString(APP_CODE).setDefaultValue(new JsonPrimitive(""))),

						CLIENT_CODE,
						Parameter.of(CLIENT_CODE,
								Schema.ofString(CLIENT_CODE).setDefaultValue(new JsonPrimitive("")))))
				.setEvents(Map.of(event.getName(), event, errorEvent.getName(), errorEvent));

	}

	@Override
	protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

		String storageName = context.getArguments()
				.get(STORAGE_NAME)
				.getAsString();

		JsonObject dataObject = context.getArguments()
				.get(DATA_OBJECT)
				.getAsJsonObject();

		JsonElement appCodeJSON = context.getArguments().get(APP_CODE);
		String appCode = appCodeJSON == null || appCodeJSON.isJsonNull() ? null : appCodeJSON.getAsString();

		JsonElement clientCodeJSON = context.getArguments().get(CLIENT_CODE);
		String clientCode = clientCodeJSON == null || clientCodeJSON.isJsonNull() ? null : clientCodeJSON.getAsString();

		if (storageName == null || dataObject == null)

			return Mono.just(new FunctionOutput(List.of(EventResult.outputOf(Map.of(EVENT_RESULT, new JsonObject())))));

		Gson gson = new Gson();
		Map<String, Object> dataObj = gson.fromJson(dataObject, new TypeToken<Map<String, Object>>() {
		}.getType());

		return appDataService.create(StringUtil.isNullOrBlank(appCode) ? null : appCode,
				StringUtil.isNullOrBlank(clientCode) ? null : clientCode, storageName,
				new DataObject().setData(dataObj))
				.map(obj -> new FunctionOutput(
						List.of(EventResult.outputOf(Map.of(EVENT_RESULT, gson.toJsonTree(obj))))));

	}

}
