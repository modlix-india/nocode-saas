package com.fincity.saas.core.functions;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

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
import com.google.gson.JsonObject;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class CreateStorageObject extends AbstractFunction {

	private static final String STORAGE_NAME = "storageName";

	private static final String DATA_OBJECT = "dataObject";

	private static final String EVENT_RESULT = "result";

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

		return new FunctionSignature().setName("CreateStorage")
		        .setNamespace("CoreServices")
		        .setParameters(Map.of(STORAGE_NAME, new Parameter().setParameterName(STORAGE_NAME)
		                .setSchema(Schema.ofString(STORAGE_NAME)), DATA_OBJECT,
		                new Parameter().setParameterName(DATA_OBJECT)
		                        .setSchema(Schema.ofObject(DATA_OBJECT))))
		        .setEvents(Map.of(event.getName(), event, errorEvent.getName(), errorEvent));

	}

	@Override
	protected FunctionOutput internalExecute(FunctionExecutionParameters context) {

		var storageName = context.getArguments()
		        .get(STORAGE_NAME);

		var dataObject = context.getArguments()
		        .get(DATA_OBJECT);

		if (storageName == null || dataObject == null)

			return new FunctionOutput(List.of(EventResult.outputOf(Map.of(EVENT_RESULT, new JsonObject()))));

		DataObject dataObj = new DataObject();
		
		dataObj.setData(dataObject.getAsJsonObject()
		        .entrySet()
		        .stream()
		        .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
		
		Mono.deferContextual(ctx -> {
			
			System.out.println("From function: " +ctx.get("appCode")+ctx.get("clientCode"));
			
			return Mono.just(123);
		}).subscribeOn(Schedulers.boundedElastic()).block();


		this.appDataService.create("", "", storageName.getAsString(), dataObj);

		return new FunctionOutput(List.of(EventResult.outputOf(Map.of(EVENT_RESULT, new JsonObject()))));
	}

}
