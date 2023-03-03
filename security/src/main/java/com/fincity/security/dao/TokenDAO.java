package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityUserToken.SECURITY_USER_TOKEN;

import org.jooq.Record1;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.security.dto.TokenObject;
import com.fincity.security.jooq.tables.records.SecurityUserTokenRecord;

import reactor.core.publisher.Flux;

@Component
public class TokenDAO extends AbstractDAO<SecurityUserTokenRecord, ULong, TokenObject> {

	protected TokenDAO() {
		super(TokenObject.class, SECURITY_USER_TOKEN, SECURITY_USER_TOKEN.ID);
	}

	public Flux<String> getTokensOfId(ULong userId) {

		return Flux.from(this.dslContext.select(SECURITY_USER_TOKEN.TOKEN).from(SECURITY_USER_TOKEN)
				.where(SECURITY_USER_TOKEN.USER_ID.eq(userId))).map(Record1::value1);
	}
}
