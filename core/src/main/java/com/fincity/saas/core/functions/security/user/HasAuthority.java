package com.fincity.saas.core.functions.security.user;

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
import com.fincity.saas.core.service.security.user.UserContextService;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;

public class HasAuthority extends AbstractReactiveFunction {

	private static final String AUTHORITY_PARAM = "authority";

	private static final String FUNCTION_NAME = "HasAuthority";

	private static final String NAME_SPACE = "CoreServices.Security.User.Context";

	private static final String EVENT_DATA = "data";

	private final UserContextService userContextService;

	public HasAuthority(UserContextService userContextService) {
		this.userContextService = userContextService;
	}

	@Override
	public FunctionSignature getSignature() {

		Event event = new Event().setName(Event.OUTPUT)
				.setParameters(Map.of(EVENT_DATA, Schema.ofBoolean(EVENT_DATA)));

		Event errorEvent = new Event().setName(Event.ERROR)
				.setParameters(Map.of(EVENT_DATA, Schema.ofBoolean(EVENT_DATA)));

		return new FunctionSignature().setNamespace(NAME_SPACE).setName(FUNCTION_NAME)
				.setParameters(Map.of(
						AUTHORITY_PARAM, Parameter.of(AUTHORITY_PARAM, Schema.ofString(AUTHORITY_PARAM))))
				.setEvents(Map.of(event.getName(), event, errorEvent.getName(), errorEvent));
	}

	@Override
	protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {

		String authority = context.getArguments().get(AUTHORITY_PARAM).getAsString();

		return userContextService.hasAuthority(authority)
				.map(hasAuthority -> new FunctionOutput(List.of(EventResult.outputOf(
						Map.of(EVENT_DATA, new JsonPrimitive(hasAuthority))))));
	}
}
