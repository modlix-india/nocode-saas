package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityClient.SECURITY_CLIENT;
import static com.fincity.security.jooq.tables.SecurityClientManage.SECURITY_CLIENT_MANAGE;
import static com.fincity.security.jooq.tables.SecurityClientPasswordPolicy.SECURITY_CLIENT_PASSWORD_POLICY;

import java.util.HashSet;
import java.util.Set;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.security.dto.Client;
import com.fincity.security.dto.ClientPasswordPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ClientDAO extends AbstractClientCheckDAO<SecurityClientRecord, ULong, Client> {

	protected ClientDAO() {
		super(Client.class, SECURITY_CLIENT, SECURITY_CLIENT.ID);
	}

	public Mono<Set<ULong>> findManagedClientList(ULong id) {

		return Flux.from(this.dslContext.select(SECURITY_CLIENT.ID)
		        .from(SECURITY_CLIENT)
		        .leftJoin(SECURITY_CLIENT_MANAGE)
		        .on(SECURITY_CLIENT_MANAGE.CLIENT_ID.eq(SECURITY_CLIENT.ID))
		        .where(SECURITY_CLIENT.ID.eq(id)
		                .or(SECURITY_CLIENT_MANAGE.MANAGE_CLIENT_ID.eq(id))))
		        .map(Record1::value1)
		        .collectList()
		        .map(HashSet::new);
	}

	public Mono<ClientPasswordPolicy> getClientPasswordPolicy(ULong clientId) {

		return Mono.from(this.dslContext.selectFrom(SECURITY_CLIENT_PASSWORD_POLICY)
		        .where(SECURITY_CLIENT_PASSWORD_POLICY.CLIENT_ID.eq(clientId))
		        .limit(1))
		        .map(e -> e.into(ClientPasswordPolicy.class));
	}

	public Mono<String> getClientType(ULong id) {

		return Flux.from(this.dslContext.select(SECURITY_CLIENT.TYPE_CODE)
		        .from(SECURITY_CLIENT)
		        .where(SECURITY_CLIENT.ID.eq(id))
		        .limit(1))
		        .take(1)
		        .singleOrEmpty()
		        .map(Record1::value1);
	}

	public Mono<Boolean> isBeingManagedBy(ULong managingClientId, ULong clientId) {

		if (managingClientId.equals(clientId))
			return Mono.just(true);

		return Mono.from(this.dslContext.select(DSL.count())
		        .from(SECURITY_CLIENT_MANAGE)
		        .where(SECURITY_CLIENT_MANAGE.CLIENT_ID.eq(clientId)
		                .and(SECURITY_CLIENT_MANAGE.MANAGE_CLIENT_ID.eq(managingClientId)))
		        .limit(1))
		        .map(Record1::value1)
		        .map(e -> e == 1);
	}

	@Override
	protected Field<ULong> getClientIDField() {

		return SECURITY_CLIENT.ID;
	}

	public Mono<Integer> addManageRecord(ULong manageClientId, ULong id) {
		return Mono.from(this.dslContext
		        .insertInto(SECURITY_CLIENT_MANAGE, SECURITY_CLIENT_MANAGE.MANAGE_CLIENT_ID,
		                SECURITY_CLIENT_MANAGE.CLIENT_ID)
		        .values(manageClientId, id));
	}
}
