package com.fincity.saas.core.functions.security;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.saas.core.service.security.ClientUrlService;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;

public class GetAppUrl extends AbstractReactiveFunction {

	private static final String FUNCTION_NAME = "GetAppUrl";

	private static final String NAME_SPACE = "CoreServices.Security";

	private static final String EVENT_DATA = "result";

	private static final String APP_CODE = "appCode";

	private static final String CLIENT_CODE = "clientCode";

	private final ClientUrlService clientUrlService;

	public GetAppUrl(ClientUrlService clientUrlService) {
		this.clientUrlService = clientUrlService;
	}

	@Override
	public FunctionSignature getSignature() {

		Event event = new Event().setName(Event.OUTPUT)
				.setParameters(Map.of(EVENT_DATA, Schema.ofString(EVENT_DATA)));

		Map<String, Parameter> parameters = new HashMap<>(Map.ofEntries(

				Parameter.ofEntry(APP_CODE, Schema.ofString(APP_CODE)),

				Parameter.ofEntry(CLIENT_CODE, Schema.ofString(CLIENT_CODE).setDefaultValue(new JsonPrimitive("")))));

		return new FunctionSignature().setName(FUNCTION_NAME).setNamespace(NAME_SPACE).setParameters(parameters)
				.setEvents(Map.of(event.getName(), event));
	}


	@Override
	protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

		String appCode = context.getArguments().get(APP_CODE).getAsString();
		String clientCode = context.getArguments().get(CLIENT_CODE).getAsString();

		Mono<String> appUrl = clientUrlService.getAppUrl(appCode, clientCode).defaultIfEmpty("");

		return appUrl.map(e -> new FunctionOutput(List.of(EventResult.outputOf(
				Map.of(EVENT_DATA, new JsonPrimitive(e))))));
	}
}
