package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityClient.SECURITY_CLIENT;
import static com.fincity.security.jooq.tables.SecurityClientUrl.SECURITY_CLIENT_URL;

import java.util.Arrays;

import org.jooq.Field;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.model.ClientUrlPattern;
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

		return filter(query).flatMapMany(cond -> Mono.from(this.dslContext.select(Arrays.asList(table.fields()))
		        .from(table)
		        .where(cond))
		        .map(e -> e.into(this.pojoClass)));
	}

	public Flux<ClientUrlPattern> readClientPatterns() {

		return Flux
		        .from(this.dslContext
		                .select(SECURITY_CLIENT_URL.CLIENT_ID, SECURITY_CLIENT.CODE, SECURITY_CLIENT_URL.URL_PATTERN,
		                        SECURITY_CLIENT_URL.APP_CODE)
		                .from(SECURITY_CLIENT_URL)
		                .leftJoin(SECURITY_CLIENT)
		                .on(SECURITY_CLIENT.ID.eq(SECURITY_CLIENT_URL.CLIENT_ID)))
		        .map(e -> new ClientUrlPattern(e.value1()
		                .toString(), e.value2(), e.value3(), e.value4()))
		        .map(ClientUrlPattern::makeHostnPort)
		        .log();
	}
}
