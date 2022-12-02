package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityClientPasswordPolicy.SECURITY_CLIENT_PASSWORD_POLICY;

import org.jooq.Field;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.security.dto.ClientPasswordPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientPasswordPolicyRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ClientPasswordPolicyDAO
        extends AbstractClientCheckDAO<SecurityClientPasswordPolicyRecord, ULong, ClientPasswordPolicy> {

	public ClientPasswordPolicyDAO() {
		super(ClientPasswordPolicy.class, SECURITY_CLIENT_PASSWORD_POLICY, SECURITY_CLIENT_PASSWORD_POLICY.ID);
	}

	public Mono<ClientPasswordPolicy> getByClientId(ULong clientId, ULong loggedInClientId) {

		return Flux.from(this.dslContext.selectFrom(SECURITY_CLIENT_PASSWORD_POLICY)
		        .where(SECURITY_CLIENT_PASSWORD_POLICY.CLIENT_ID.eq(clientId)
		                .or(SECURITY_CLIENT_PASSWORD_POLICY.CLIENT_ID.eq(loggedInClientId))))
		        .map(e -> e.into(ClientPasswordPolicy.class))
		        .collectList()
		        .flatMap(e ->
				{
			        if (e.isEmpty())
				        return Mono.empty();

			        if (e.size() == 1)
				        return Mono.just(e.get(0));

			        return Mono.just(e.get(clientId.equals(e.get(0)
			                .getClientId()) ? 0 : 1));
		        });

	}

	@Override
	protected Field<ULong> getClientIDField() {
		return SECURITY_CLIENT_PASSWORD_POLICY.CLIENT_ID;
	}

}
