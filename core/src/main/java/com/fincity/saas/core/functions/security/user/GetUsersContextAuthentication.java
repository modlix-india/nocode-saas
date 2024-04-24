package com.fincity.saas.core.functions.security.user;

import java.util.function.Function;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.core.service.security.user.UserContextService;

import reactor.core.publisher.Mono;

public class GetUsersContextAuthentication
		extends AbstractUserContextFunction<ContextAuthentication> {

	private static final String FUNCTION_NAME = "GetUsersContextAuthentication";

	public GetUsersContextAuthentication(UserContextService userContextService) {
		super(userContextService);
	}

	@Override
	protected Function<UserContextService, Mono<ContextAuthentication>> getServiceCallFunction() {
		return UserContextService::getUsersContextAuthentication;
	}

	@Override
	protected String getFunctionName() {
		return FUNCTION_NAME;
	}
}
