package com.fincity.saas.core.functions.storage;

import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.nocode.kirun.engine.util.string.StringUtil;
import com.fincity.saas.core.service.connection.appdata.AppDataService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;

public class ReadStorageObject extends AbstractReactiveFunction {

	private static final String DATA_OBJECT_ID = "dataObjectId";

	private static final String EVENT_RESULT = "result";

	private static final String FUNCTION_NAME = "Read";

	private static final String NAME_SPACE = "CoreServices.Storage";

	private static final String STORAGE_NAME = "storageName";

	private static final String APP_CODE = "appCode";

	private static final String CLIENT_CODE = "clientCode";

	private static final String EAGER = "eager";

	private static final String EAGER_FIELDS = "eagerFields";

	private final AppDataService appDataService;
	private final Gson gson;

	public ReadStorageObject(AppDataService appDataService, Gson gson) {

		this.appDataService = appDataService;
		this.gson = gson;
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

						DATA_OBJECT_ID, Parameter.of(DATA_OBJECT_ID, Schema.ofString(DATA_OBJECT_ID)),

						APP_CODE,
						Parameter.of(APP_CODE,
								Schema.ofString(APP_CODE).setDefaultValue(new JsonPrimitive(""))),

						CLIENT_CODE,
						Parameter.of(CLIENT_CODE,
								Schema.ofString(CLIENT_CODE).setDefaultValue(new JsonPrimitive(""))),

						EAGER,
						Parameter.of(EAGER, Schema.ofBoolean(EAGER).setDefaultValue(new JsonPrimitive(false))),

						EAGER_FIELDS,
						Parameter.of(EAGER_FIELDS, Schema.ofString(EAGER_FIELDS), true)))

				.setEvents(Map.of(event.getName(), event, errorEvent.getName(), errorEvent));
	}

	@Override
	protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

		String storageName = context.getArguments()
				.get(STORAGE_NAME)
				.getAsString();

		String dataObjectId = context.getArguments()
				.get(DATA_OBJECT_ID)
				.getAsString();

		JsonElement appCodeJSON = context.getArguments().get(APP_CODE);
		String appCode = appCodeJSON == null || appCodeJSON.isJsonNull() ? null : appCodeJSON.getAsString();

		JsonElement clientCodeJSON = context.getArguments().get(CLIENT_CODE);
		String clientCode = clientCodeJSON == null || clientCodeJSON.isJsonNull() ? null : clientCodeJSON.getAsString();

		boolean eager = context.getArguments().get(EAGER).getAsBoolean();

		List<String> eagerFields = StreamSupport
				.stream(context.getArguments().get(EAGER_FIELDS).getAsJsonArray().spliterator(), false)
				.map(JsonElement::getAsString).toList();

		if (storageName == null)
			return Mono.just(new FunctionOutput(List.of(EventResult.of(Event.ERROR,
					Map.of(Event.ERROR, new JsonPrimitive("Please provide the storage name."))))));

		if (dataObjectId == null)

			return Mono.just(new FunctionOutput(List.of(EventResult.of(Event.ERROR,
					Map.of(Event.ERROR, new JsonPrimitive("Please provide the data object id."))))));

		return this.appDataService.read(StringUtil.isNullOrBlank(appCode) ? null : appCode,
				StringUtil.isNullOrBlank(clientCode) ? null : clientCode, storageName, dataObjectId, eager, eagerFields)
				.map(receivedObject -> new FunctionOutput(
						List.of(EventResult.outputOf(Map.of(EVENT_RESULT, gson.toJsonTree(receivedObject))))));
	}

}
