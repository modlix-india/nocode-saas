package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityClient.SECURITY_CLIENT;
import static com.fincity.security.jooq.tables.SecurityClientManage.SECURITY_CLIENT_MANAGE;
import static com.fincity.security.jooq.tables.SecurityClientPasswordPolicy.SECURITY_CLIENT_PASSWORD_POLICY;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.jooq.Record1;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.security.dto.Client;
import com.fincity.security.dto.ClientPasswordPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ClientDAO extends AbstractUpdatableDAO<SecurityClientRecord, ULong, Client> {

	protected ClientDAO() {
		super(Client.class, SECURITY_CLIENT, SECURITY_CLIENT.ID);
	}

//	public Mono<Void> addClientURLPattern(ULong clientId, String urlPattern) {
//
//		return Mono
//		        .from(this.dslContext
//		                .insertInto(SECURITY_CLIENT_URL, SECURITY_CLIENT_URL.CLIENT_ID, SECURITY_CLIENT_URL.URL_PATTERN)
//		                .values(clientId, urlPattern))
//		        .then();
//	}

//	public Mono<List<ClientURLPattern>> clientURLPatterns() {
//
//		return Flux.from(this.dslContext.select(SECURITY_CLIENT_URL.CLIENT_ID, SECURITY_CLIENT_URL.URL_PATTERN)
//		        .from(SECURITY_CLIENT_URL))
//		        .map(e -> e.into(ClientURLPattern.class))
//		        .collectList();
//	}

	public Mono<Set<ULong>> findManagedClientList(ULong id) {

		return Flux.from(this.dslContext.select(SECURITY_CLIENT_MANAGE.CLIENT_ID)
		        .from(SECURITY_CLIENT_MANAGE)
		        .where(SECURITY_CLIENT_MANAGE.MANAGE_CLIENT_ID.eq(id)))
		        .map(Record1::value1)
		        .collect(Collectors.toCollection(HashSet::new))
		        .map(e ->
				{
			        e.add(id);
			        return e;
		        })
		        .map(e -> (Set<ULong>) e)
		        .switchIfEmpty(Mono.just(Set.of(id)));
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

}
