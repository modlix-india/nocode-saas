package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityApp.SECURITY_APP;
import static com.fincity.security.jooq.tables.SecurityClientPasswordPolicy.SECURITY_CLIENT_PASSWORD_POLICY;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.security.dto.ClientPasswordPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientPasswordPolicyRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ClientPasswordPolicyDAO
        extends AbstractClientCheckDAO<SecurityClientPasswordPolicyRecord, ULong, ClientPasswordPolicy> {

	@Override
	protected Field<ULong> getClientIDField() {
		return SECURITY_CLIENT_PASSWORD_POLICY.CLIENT_ID;
	}

	public ClientPasswordPolicyDAO() {
		super(ClientPasswordPolicy.class, SECURITY_CLIENT_PASSWORD_POLICY, SECURITY_CLIENT_PASSWORD_POLICY.ID);
	}

	public Mono<Boolean> checkValidEntity(ULong clientId) {

		return Mono.from(this.dslContext.selectCount()
		        .from(SECURITY_CLIENT_PASSWORD_POLICY)
		        .where(SECURITY_CLIENT_PASSWORD_POLICY.CLIENT_ID.eq(clientId)
		                .and(SECURITY_CLIENT_PASSWORD_POLICY.APP_ID.isNull())))
		        .map(Record1::value1)
		        .flatMap(e -> Mono.justOrEmpty(e == 0 ? true : null));

	}

	public Mono<ClientPasswordPolicy> getByAppCodeAndClient(String appCode, ULong clientId, ULong loggedInClientId) {

		return Flux.from(this.dslContext.select(SECURITY_CLIENT_PASSWORD_POLICY.fields())
		        .from(SECURITY_CLIENT_PASSWORD_POLICY)
		        .leftJoin(SECURITY_APP)
		        .on(SECURITY_CLIENT_PASSWORD_POLICY.APP_ID.eq(SECURITY_APP.ID))
		        .where(SECURITY_CLIENT_PASSWORD_POLICY.CLIENT_ID.in(clientId, loggedInClientId)
		                .and(SECURITY_APP.APP_CODE.eq(appCode)
		                        .or(SECURITY_APP.APP_CODE.isNull())))
		        .orderBy(
		                loggedInClientId.longValue() < clientId.longValue()
		                        ? SECURITY_CLIENT_PASSWORD_POLICY.CLIENT_ID.desc()
		                        : SECURITY_CLIENT_PASSWORD_POLICY.CLIENT_ID.asc(),
		                SECURITY_CLIENT_PASSWORD_POLICY.APP_ID.desc()))
		        .map(e -> e.into(ClientPasswordPolicy.class))
		        .collectList()
		        .flatMap(e ->
				{

			        if (e.isEmpty())
				        return Mono.empty();

			        return Mono.just(e.get(0));
		        });

	}

}
