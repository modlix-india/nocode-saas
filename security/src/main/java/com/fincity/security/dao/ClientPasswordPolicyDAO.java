package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityClientPasswordPolicy.SECURITY_CLIENT_PASSWORD_POLICY;
import static com.fincity.security.jooq.tables.SecurityPastPasswords.SECURITY_PAST_PASSWORDS;

import java.util.HashSet;
import java.util.Set;

import org.jooq.Record1;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.ClientPasswordPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientPasswordPolicyRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ClientPasswordPolicyDAO
        extends AbstractUpdatableDAO<SecurityClientPasswordPolicyRecord, ULong, ClientPasswordPolicy> {

	public ClientPasswordPolicyDAO() {
		super(ClientPasswordPolicy.class, SECURITY_CLIENT_PASSWORD_POLICY, SECURITY_CLIENT_PASSWORD_POLICY.ID);
	}

	public Mono<ClientPasswordPolicy> getByClientId(ULong clientId) {

		return Mono.from(this.dslContext.selectFrom(SECURITY_CLIENT_PASSWORD_POLICY)
		        .where(SECURITY_CLIENT_PASSWORD_POLICY.CLIENT_ID.eq(clientId)))
		        .map(e -> e.into(ClientPasswordPolicy.class));

	}

	public Mono<Set<String>> getPastPasswords(ULong userId) {

		return Flux.from(this.dslContext.select(SECURITY_PAST_PASSWORDS.PASSWORD)
		        .from(SECURITY_PAST_PASSWORDS)
		        .where(SECURITY_PAST_PASSWORDS.USER_ID.eq(userId))
		        .orderBy(SECURITY_PAST_PASSWORDS.CREATED_AT))
		        .map(Record1::value1)
		        .collectList()
		        .map(HashSet::new);
	}

	public Mono<Boolean> checkClientPasswordPolicyExists(ULong clientId) {
		return Mono.from(this.dslContext.selectCount()
		        .from(SECURITY_CLIENT_PASSWORD_POLICY)
		        .where(SECURITY_CLIENT_PASSWORD_POLICY.CLIENT_ID.eq(clientId)))
		        .map(Record1::value1)
		        .map(count -> count > 0);

	}
}
