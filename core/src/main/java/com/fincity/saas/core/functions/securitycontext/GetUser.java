package com.fincity.saas.core.functions.securitycontext;

import java.util.List;
import java.util.Map;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Event;
import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.core.kirun.repository.CoreSchemaRepository;
import com.fincity.saas.core.service.security.ContextService;
import com.google.gson.Gson;

import reactor.core.publisher.Mono;

public class GetUser extends AbstractReactiveFunction {

	private static final String FUNCTION_NAME = "GetUser";
	private static final String NAME_SPACE = "CoreServices.SecurityContext";
	private static final String EVENT_DATA_USER = "user";

	private final ContextService userContextService;

	public GetUser(ContextService userContextService) {
		this.userContextService = userContextService;
	}

	@Override
	public FunctionSignature getSignature() {

		Event event = new Event().setName(Event.OUTPUT)
				.setParameters(
						Map.of(EVENT_DATA_USER, Schema.ofRef(CoreSchemaRepository.SCHEMA_NAMESPACE_SECURITY_CONTEXT
								+ "." + CoreSchemaRepository.SCHEMA_NAME_CONTEXT_USER)));

		return new FunctionSignature().setNamespace(NAME_SPACE).setName(FUNCTION_NAME)
				.setEvents(Map.of(event.getName(), event));
	}

	@Override
	protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {
		return userContextService.getUsersContextUser()
				.map(contextUser -> new FunctionOutput(List.of(EventResult.outputOf(
						Map.of(EVENT_DATA_USER,
								new Gson().toJsonTree(contextUser, ContextUser.class))))));
	}
}
