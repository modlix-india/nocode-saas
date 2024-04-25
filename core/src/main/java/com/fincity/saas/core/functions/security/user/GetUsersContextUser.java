package com.fincity.saas.core.functions.security.user;

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
import com.fincity.saas.core.service.security.user.UserContextService;
import com.google.gson.Gson;

import reactor.core.publisher.Mono;

public class GetUsersContextUser extends AbstractReactiveFunction {

	private static final String FUNCTION_NAME = "GetUsersContextUser";

	private static final String NAME_SPACE = "CoreServices.Security.User.Context";

	private static final String EVENT_DATA = "data";

	private final UserContextService userContextService;
	private final String objectNameSpace;
	private final String objectName;

	public GetUsersContextUser(UserContextService userContextService, String objectNameSpace, String objectName) {
		this.userContextService = userContextService;
		this.objectNameSpace = objectNameSpace;
		this.objectName = objectName;
	}

	@Override
	public FunctionSignature getSignature() {

		Event event = new Event().setName(Event.OUTPUT)
				.setParameters(Map.of(EVENT_DATA, Schema.ofRef(objectNameSpace + "." + objectName)));

		Event errorEvent = new Event().setName(Event.ERROR)
				.setParameters(Map.of(EVENT_DATA, Schema.ofRef(objectNameSpace + "." + objectName)));

		return new FunctionSignature().setNamespace(NAME_SPACE).setName(FUNCTION_NAME)
				.setEvents(Map.of(event.getName(), event, errorEvent.getName(), errorEvent));
	}

	@Override
	protected Mono<FunctionOutput> internalExecute(ReactiveFunctionExecutionParameters context) {
		return userContextService.getUsersContextUser()
				.map(contextUser -> new FunctionOutput(List.of(EventResult.outputOf(
						Map.of(EVENT_DATA,
								new Gson().toJsonTree(contextUser, ContextUser.class))))));
	}
}
