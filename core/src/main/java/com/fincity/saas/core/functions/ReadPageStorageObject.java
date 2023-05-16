package com.fincity.saas.core.functions;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.core.service.connection.appdata.AppDataService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;

public class ReadPageStorageObject extends AbstractReactiveFunction {

	private static final String EVENT_RESULT = "result";

	private static final String FUNCTION_NAME = "ReadPageStorage";

	private static final String NAME_SPACE = "CoreServices";

	private static final String STORAGE_NAME = "storageName";

	private static final String FILTER = "filter";

	private static final String PAGE = "page";

	private static final String SIZE = "size";

	private AppDataService appDataService;

	public ReadPageStorageObject(AppDataService appDataService) {

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

		return new FunctionSignature().setNamespace(NAME_SPACE)
		        .setName(FUNCTION_NAME)
		        .setParameters(Map.of(

		                STORAGE_NAME, Parameter.of(STORAGE_NAME, Schema.ofString(STORAGE_NAME)),

		                FILTER, Parameter.of(FILTER, new Schema().setOneOf(List.of(Schema.ofObject(FILTER)
		                        .setDefaultValue(new JsonObject()), new Schema().setType(Type.of(SchemaType.NULL))))),

		                PAGE, Parameter.of(PAGE, Schema.ofInteger(PAGE)
		                        .setDefaultValue(new JsonPrimitive(10))),

		                SIZE, Parameter.of(SIZE, Schema.ofInteger(SIZE)
		                        .setDefaultValue(new JsonPrimitive(15)))))

		        .setEvents(Map.of(event.getName(), event, errorEvent.getName(), errorEvent));
	}

	@Override
	protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

		String storageName = context.getArguments()
		        .get(STORAGE_NAME)
		        .getAsString();

		JsonObject filter = context.getArguments()
		        .get(FILTER) != null ? context.getArguments()
		                .get(FILTER)
		                .getAsJsonObject()
		                : this.getSignature()
		                        .getParameters()
		                        .get(FILTER)
		                        .getSchema()
		                        .getDefaultValue()
		                        .getAsJsonObject();

		Integer page = context.getArguments()
		        .get(PAGE) != null ? context.getArguments()
		                .get(PAGE)
		                .getAsInt()
		                : this.getSignature()
		                        .getParameters()
		                        .get(PAGE)
		                        .getSchema()
		                        .getDefaultValue()
		                        .getAsInt();

		Integer size = context.getArguments()
		        .get(SIZE) != null ? context.getArguments()
		                .get(SIZE)
		                .getAsInt()
		                : this.getSignature()
		                        .getParameters()
		                        .get(SIZE)
		                        .getSchema()
		                        .getDefaultValue()
		                        .getAsInt();

		if (storageName == null)
			return Mono.just(new FunctionOutput(List.of(EventResult.of(Event.ERROR,
			        Map.of(Event.ERROR, new JsonPrimitive("Please provide the storage name."))))));

		// update the readpage function

		Pageable pageable = PageRequest.of(page, size);

		Gson gson = new GsonBuilder().create();

		return this.appDataService.readPage(null, null, storageName, pageable, true, new ComplexCondition())
		        .map(receivedObject -> new FunctionOutput(
		                List.of(EventResult.outputOf(Map.of(EVENT_RESULT, gson.toJsonTree(receivedObject))))));
	}

}
