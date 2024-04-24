package com.fincity.saas.core.functions.security.user;

import java.util.Map;
import java.util.function.BiFunction;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.saas.core.service.security.user.UserContextService;

import reactor.core.publisher.Mono;

public class HasAuthority extends AbstractUserContextFunction<Boolean> {

	private static final String AUTHORITY_PARAM = "authority";

	private static final String FUNCTION_NAME = "HasAuthority";

	public HasAuthority(UserContextService userContextService) {
		super(userContextService);
	}

	@Override
	protected BiFunction<UserContextService, ReactiveFunctionExecutionParameters, Mono<Boolean>> getServiceCallFunctionWithContext() {
		return (service, context) -> {
			String authority = context.getArguments().get(AUTHORITY_PARAM).getAsString();
			return service.hasAuthority(authority);
		};
	}

	@Override
	protected String getFunctionName() {
		return FUNCTION_NAME;
	}

	@Override
	protected Map<String, Parameter> getAdditionalParameters() {
		return Map.of(AUTHORITY_PARAM, Parameter.of(AUTHORITY_PARAM, Schema.ofString(AUTHORITY_PARAM)));
	}
}
