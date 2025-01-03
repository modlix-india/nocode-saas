package com.fincity.security.service.policy;

import org.jooq.types.ULong;

import com.fincity.security.dto.policy.AbstractPolicy;
import com.fincity.security.model.AuthenticationPasswordType;

import reactor.core.publisher.Mono;

public interface IPolicyService<T extends AbstractPolicy> {

	AuthenticationPasswordType getAuthenticationPasswordType();

	String getPolicyCacheName();

	Mono<T> read(ULong clientId, ULong appId);

	Mono<Boolean> checkAllConditions(ULong clientId, ULong appId, ULong userId, String password);

	Mono<String> generatePolicyPassword(ULong clientId, ULong appId);
}
