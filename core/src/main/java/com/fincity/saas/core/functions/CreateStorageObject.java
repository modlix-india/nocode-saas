package com.fincity.saas.core.functions;

import java.util.List;
import java.util.Map;

import com.fincity.nocode.kirun.engine.function.AbstractFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.FunctionExecutionParameters;
import com.fincity.saas.core.model.DataObject;
import com.fincity.saas.core.service.connection.appdata.AppDataService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import reactor.core.scheduler.Schedulers;

public class CreateStorageObject extends AbstractFunction {

	private static final String DATA_OBJECT = "dataObject";

	private static final String EVENT_RESULT = "result";

	private static final String NAME = "CreateStorage";

	private static final String NAME_SPACE = "CoreServices";

	private static final String STORAGE_NAME = "storageName";

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

		return new FunctionSignature().setName(NAME)
		        .setNamespace(NAME_SPACE)
		        .setParameters(Map.of(STORAGE_NAME, new Parameter().setParameterName(STORAGE_NAME)
		                .setSchema(Schema.ofString(STORAGE_NAME)), DATA_OBJECT,
		                new Parameter().setParameterName(DATA_OBJECT)
		                        .setSchema(Schema.ofObject(DATA_OBJECT))))
		        .setEvents(Map.of(event.getName(), event, errorEvent.getName(), errorEvent));

	}

	@Override
	protected FunctionOutput internalExecute(FunctionExecutionParameters context) {

		String storageName = context.getArguments()
		        .get(STORAGE_NAME)
		        .getAsString();

		JsonObject dataObject = context.getArguments()
		        .get(DATA_OBJECT)
		        .getAsJsonObject();

		if (storageName == null || dataObject == null)

			return new FunctionOutput(List.of(EventResult.outputOf(Map.of(EVENT_RESULT, new JsonObject()))));

		Gson gson = new Gson();
		Map<String, Object> dataObj = gson.fromJson(dataObject, new TypeToken<Map<String, Object>>() {
		}.getType());

		var retobj = appDataService.create(null, null, storageName, new DataObject().setData(dataObj))
		        .subscribeOn(Schedulers.boundedElastic())
		        .block();

		var obj = gson.toJsonTree(retobj);

		return new FunctionOutput(List.of(EventResult.outputOf(Map.of(EVENT_RESULT, obj))));

	}

}
