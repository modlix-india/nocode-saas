package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityApp.SECURITY_APP;
import static com.fincity.security.jooq.tables.SecurityClientPasswordPolicy.SECURITY_CLIENT_PASSWORD_POLICY;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;
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

	public Mono<Boolean> checkValidEntity(ULong clientId) {

		return Flux.from(this.dslContext.selectFrom(SECURITY_CLIENT_PASSWORD_POLICY)
		        .where(SECURITY_CLIENT_PASSWORD_POLICY.CLIENT_ID.eq(clientId))
		        .except(this.dslContext.selectFrom(SECURITY_CLIENT_PASSWORD_POLICY)
		                .where(SECURITY_CLIENT_PASSWORD_POLICY.CLIENT_ID.eq(clientId)
		                        .and(SECURITY_CLIENT_PASSWORD_POLICY.APP_ID.isNotNull()))))
		        .collectList()
		        .flatMap(e -> e.isEmpty() ? Mono.just(true) : Mono.empty());
	}

	public Mono<ClientPasswordPolicy> getByClientId(ULong clientId, ULong loggedInClientId) {

		Condition cond = DSL.and(SECURITY_CLIENT_PASSWORD_POLICY.CLIENT_ID.eq(clientId)
		        .or(SECURITY_CLIENT_PASSWORD_POLICY.CLIENT_ID.eq(loggedInClientId)));

		return Flux.from(this.dslContext.selectFrom(SECURITY_CLIENT_PASSWORD_POLICY)
		        .where(cond)
		        .except(this.dslContext.selectFrom(SECURITY_CLIENT_PASSWORD_POLICY)
		                .where(cond.and(SECURITY_CLIENT_PASSWORD_POLICY.APP_ID.isNotNull()))))
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

	// fetch policy by app id and client id

	public Mono<ClientPasswordPolicy> getByAppAndClient(ULong appId, ULong clientId, ULong loggedInClientId) {

		Condition cond = DSL.and(SECURITY_CLIENT_PASSWORD_POLICY.CLIENT_ID.eq(clientId)
		        .or(SECURITY_CLIENT_PASSWORD_POLICY.CLIENT_ID.eq(loggedInClientId)))
		        .and(SECURITY_CLIENT_PASSWORD_POLICY.APP_ID.eq(appId));

		return Flux.from(this.dslContext.selectFrom(SECURITY_CLIENT_PASSWORD_POLICY)
		        .where(cond))
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

	public Mono<ClientPasswordPolicy> getByAppCodeAndClient(String appCode, ULong clientId, ULong loggedInClientId) {

		Condition cond = DSL.and(SECURITY_CLIENT_PASSWORD_POLICY.CLIENT_ID.eq(clientId)
		        .or(SECURITY_CLIENT_PASSWORD_POLICY.CLIENT_ID.eq(loggedInClientId)));

		return Flux.from(this.dslContext.select(SECURITY_CLIENT_PASSWORD_POLICY.fields())
		        .from(SECURITY_CLIENT_PASSWORD_POLICY)
		        .leftJoin(SECURITY_APP)
		        .on(SECURITY_CLIENT_PASSWORD_POLICY.APP_ID.eq(SECURITY_APP.ID))
		        .where(cond.and(SECURITY_APP.APP_CODE.eq(appCode))))
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

}
