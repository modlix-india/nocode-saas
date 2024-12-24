package com.fincity.security.dao.policy;

import static com.fincity.security.jooq.tables.SecurityClientPasswordPolicy.SECURITY_CLIENT_PASSWORD_POLICY;
import static com.fincity.security.jooq.tables.SecurityPastPasswords.SECURITY_PAST_PASSWORDS;

import java.util.List;

import org.jooq.Field;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.security.dto.PastPassword;
import com.fincity.security.dto.policy.ClientPasswordPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientPasswordPolicyRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ClientPasswordPolicyDAO
		extends AbstractPolicyDao<SecurityClientPasswordPolicyRecord, ClientPasswordPolicy> {

	public ClientPasswordPolicyDAO() {
		super(ClientPasswordPolicy.class, SECURITY_CLIENT_PASSWORD_POLICY, SECURITY_CLIENT_PASSWORD_POLICY.ID);
	}

	@Override
	protected Field<ULong> getClientIDField() {
		return SECURITY_CLIENT_PASSWORD_POLICY.CLIENT_ID;
	}

	@Override
	protected Field<ULong> getAppIdField() {
		return SECURITY_CLIENT_PASSWORD_POLICY.APP_ID;
	}

	public Mono<List<PastPassword>> getPastPasswordsBasedOnPolicy(ClientPasswordPolicy clientPasswordPolicy, ULong userId) {

		return Flux.from(this.dslContext.select(SECURITY_PAST_PASSWORDS.fields())
						.from(SECURITY_PAST_PASSWORDS)
						.where(SECURITY_PAST_PASSWORDS.USER_ID.eq(userId))
						.orderBy(SECURITY_PAST_PASSWORDS.CREATED_AT.desc())
						.limit(clientPasswordPolicy.getPassHistoryCount()))
				.map(e -> e.into(PastPassword.class))
				.collectList()
				.defaultIfEmpty(List.of());
	}
}
