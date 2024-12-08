package com.fincity.saas.core.dao;

import static com.fincity.saas.core.jooq.tables.CoreTokens.CORE_TOKENS;

import java.time.LocalDateTime;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.saas.core.dto.CoreToken;
import com.fincity.saas.core.jooq.tables.records.CoreTokensRecord;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Component
public class CoreTokenDAO extends AbstractDAO<CoreTokensRecord, ULong, CoreToken> {

	protected CoreTokenDAO() {
		super(CoreToken.class, CORE_TOKENS, CORE_TOKENS.ID);
	}

	public Mono<Tuple2<String, LocalDateTime>> getActiveAccessToken(String clientCode, String appCode, String connectionName) {
		return Mono.from(this.dslContext.selectFrom(CORE_TOKENS)
						.where(CORE_TOKENS.CLIENT_CODE.eq(clientCode))
						.and(CORE_TOKENS.APP_CODE.eq(appCode))
						.and(CORE_TOKENS.CONNECTION_NAME.eq(connectionName))
						.and(CORE_TOKENS.IS_REVOKED.eq((byte) 0))
						.and(CORE_TOKENS.EXPIRES_AT.greaterThan(LocalDateTime.now())))
				.map(result -> Tuples.of(result.get(CORE_TOKENS.TOKEN), result.get(CORE_TOKENS.EXPIRES_AT)));
	}

}
