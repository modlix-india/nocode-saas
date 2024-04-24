package com.fincity.saas.core.functions.security.user;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.saas.core.service.security.user.UserContextService;
import com.google.gson.Gson;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;

public abstract class AbstractUserContextFunction<T> extends AbstractReactiveFunction {

	private static final String EVENT_RESULT = "result";
	private static final String NAME_SPACE = "CoreServices.Security.User.Context";
	private static final String APP_CODE = "appCode";
	private static final String CLIENT_CODE = "clientCode";

	private final UserContextService userContextService;

	protected AbstractUserContextFunction(UserContextService userContextService) {
		this.userContextService = userContextService;
	}

	protected Function<UserContextService, Mono<T>> getServiceCallFunction() {
		return null;
	}

	protected BiFunction<UserContextService, ReactiveFunctionExecutionParameters, Mono<T>> getServiceCallFunctionWithContext() {
		return null;
	}

	protected abstract String getFunctionName();

	protected Map<String, Parameter> getAdditionalParameters() {
		return Map.of();
	}

	@Override
	protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {
		Mono<?> result = getServiceCallFunctionWithContext() != null
				? getServiceCallFunctionWithContext().apply(userContextService, context)
				: getServiceCallFunction().apply(userContextService);

		return result.map(
				r -> new FunctionOutput(List.of(EventResult.outputOf(Map.of(EVENT_RESULT, new Gson().toJsonTree(r))))));
	}

	@Override
	public FunctionSignature getSignature() {

		Event event = new Event().setName(Event.OUTPUT).setParameters(Map.of(EVENT_RESULT, Schema.ofAny(EVENT_RESULT)));

		Event errorEvent = new Event().setName(Event.ERROR)
				.setParameters(Map.of(EVENT_RESULT, Schema.ofAny(EVENT_RESULT)));

		Map<String, Parameter> allParameters = new HashMap<>(
				Map.of(
						APP_CODE, Parameter.of(APP_CODE,
								Schema.ofString(APP_CODE).setDefaultValue(new JsonPrimitive(""))),
						CLIENT_CODE, Parameter.of(CLIENT_CODE,
								Schema.ofString(CLIENT_CODE).setDefaultValue(new JsonPrimitive("")))));

		allParameters.putAll(getAdditionalParameters());

		return new FunctionSignature()
				.setNamespace(NAME_SPACE)
				.setName(getFunctionName())
				.setParameters(allParameters)
				.setEvents(Map.of(event.getName(), event, errorEvent.getName(), errorEvent));
	}
}
