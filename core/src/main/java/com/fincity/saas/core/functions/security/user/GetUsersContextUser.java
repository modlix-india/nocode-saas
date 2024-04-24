package com.fincity.saas.core.functions.security.user;

import java.util.function.Function;

import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.core.service.security.user.UserContextService;

import reactor.core.publisher.Mono;

public class GetUsersContextUser extends AbstractUserContextFunction<ContextUser> {

	private static final String FUNCTION_NAME = "GetUsersContextUser";

	public GetUsersContextUser(UserContextService userContextService) {
		super(userContextService);
	}

	@Override
	protected Function<UserContextService, Mono<ContextUser>> getServiceCallFunction() {
		return UserContextService::getUsersContextUser;
	}

	@Override
	protected String getFunctionName() {
		return FUNCTION_NAME;
	}
}
