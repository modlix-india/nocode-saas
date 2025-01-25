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
import com.fincity.saas.core.exception.StorageObjectNotFoundException;
import com.fincity.saas.core.model.DataObject;
import com.fincity.saas.core.service.connection.appdata.AppDataService;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;

public class UpdateStorageObject extends AbstractReactiveFunction {

	private static final String DATA_OBJECT_ID = "dataObjectId";

	private static final String DATA_OBJECT = "dataObject";

	private static final String EVENT_RESULT = "result";

	private static final String FUNCTION_NAME = "Update";

	private static final String NAME_SPACE = "CoreServices.Storage";

	private static final String STORAGE_NAME = "storageName";

	private static final String ISPARTIAL = "isPartial";

	private static final String ID = "_id";

	private static final String APP_CODE = "appCode";

	private static final String CLIENT_CODE = "clientCode";

	private static final String EAGER = "eager";

	private static final String EAGER_FIELDS = "eagerFields";

	private final AppDataService appDataService;
	private final Gson gson;

	public UpdateStorageObject(AppDataService appDataService, Gson gson) {
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

						ISPARTIAL, Parameter.of(ISPARTIAL, Schema.ofBoolean(ISPARTIAL)
								.setDefaultValue(new JsonPrimitive(false))),

						DATA_OBJECT_ID, Parameter.of(DATA_OBJECT_ID, Schema.ofString(DATA_OBJECT_ID)),

						DATA_OBJECT, Parameter.of(DATA_OBJECT, Schema.ofObject(DATA_OBJECT)),

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

		Boolean isPartial = context.getArguments()
				.get(ISPARTIAL)
				.getAsBoolean();

		String dataObjectId = context.getArguments()
				.get(DATA_OBJECT_ID)
				.getAsString();

		JsonObject updatableObject = context.getArguments()
				.get(DATA_OBJECT)
				.getAsJsonObject();

		boolean eager = context.getArguments().get(EAGER).getAsBoolean();

		List<String> eagerFields = StreamSupport
				.stream(context.getArguments().get(EAGER_FIELDS).getAsJsonArray().spliterator(), false)
				.map(JsonElement::getAsString).toList();

		JsonElement appCodeJSON = context.getArguments().get(APP_CODE);
		String appCode = appCodeJSON == null || appCodeJSON.isJsonNull() ? null : appCodeJSON.getAsString();

		JsonElement clientCodeJSON = context.getArguments().get(CLIENT_CODE);
		String clientCode = clientCodeJSON == null || clientCodeJSON.isJsonNull() ? null : clientCodeJSON.getAsString();

		if (storageName == null)
			return Mono.just(new FunctionOutput(List.of(EventResult.of(Event.ERROR,
					Map.of(Event.ERROR, new JsonPrimitive("Please provide the storage name."))))));

		if (dataObjectId == null || updatableObject == null)
			return Mono.just(new FunctionOutput(List.of(EventResult.of(Event.ERROR, Map.of(Event.ERROR,
					new JsonPrimitive("Please provide the id for which delete needs to be performed."))))));

		Map<String, Object> updatableDataObject = gson.fromJson(updatableObject, new TypeToken<Map<String, Object>>() {
		}.getType());

		updatableDataObject.put(ID, dataObjectId);

		DataObject dataObject = new DataObject().setData(updatableDataObject);

		return appDataService
				.update(StringUtil.isNullOrBlank(appCode) ? null : appCode,
						StringUtil.isNullOrBlank(clientCode) ? null : clientCode, storageName, dataObject, !isPartial,
						eager, eagerFields)
				.onErrorResume(exception -> exception instanceof StorageObjectNotFoundException ? Mono.just(Map.of())
						: Mono.error(exception))
				.map(updatedObject -> new FunctionOutput(
						List.of(EventResult.outputOf(
								Map.of(EVENT_RESULT, gson.toJsonTree(updatedObject))))));
	}

}
