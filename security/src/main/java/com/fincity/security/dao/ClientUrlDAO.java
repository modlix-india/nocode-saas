package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityClientPasswordPolicy.SECURITY_CLIENT_PASSWORD_POLICY;
import static com.fincity.security.jooq.tables.SecurityClientUrl.SECURITY_CLIENT_URL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.security.dto.ClientPasswordPolicy;
import com.fincity.security.dto.ClientUrl;
import com.fincity.security.jooq.tables.records.SecurityClientUrlRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ClientUrlDAO extends AbstractClientCheckDAO<SecurityClientUrlRecord, ULong, ClientUrl> {

	public ClientUrlDAO() {
		super(ClientUrl.class, SECURITY_CLIENT_URL, SECURITY_CLIENT_URL.ID);
	}

	@Override
	protected Field<ULong> getClientIDField() {
		return SECURITY_CLIENT_URL.CLIENT_ID;
	}

	@Override
	public Flux<ClientUrl> readAll(AbstractCondition query) {

		return filter(query).flatMapMany(
				cond -> Mono.from(this.dslContext.select(Arrays.asList(table.fields())).from(table).where(cond))
						.map(e -> e.into(this.pojoClass)));
	}

	public Mono<ClientPasswordPolicy> getClientPasswordPolicy(ULong clientId) {

		return Mono
				.from(this.dslContext.selectFrom(SECURITY_CLIENT_PASSWORD_POLICY)
						.where(SECURITY_CLIENT_PASSWORD_POLICY.CLIENT_ID.eq(clientId)).limit(1))
				.map(e -> e.into(ClientPasswordPolicy.class));
	}

	
	public Mono<List<String>> getClientUrlsBasedOnAppAndClient(String appCode, ULong clientId) {

		List<Condition> conds = new ArrayList<>();

		conds.add(SECURITY_CLIENT_URL.APP_CODE.eq(appCode));

		if (!clientId.equals(null))
			conds.add(SECURITY_CLIENT_URL.CLIENT_ID.eq(clientId));

		return Flux.from(
				this.dslContext.select(SECURITY_CLIENT_URL.URL_PATTERN).from(SECURITY_CLIENT_URL).where(DSL.and(conds)))
				.map(Record1::value1).collectList();
	}
}
