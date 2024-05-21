package com.fincity.saas.core.dao;

import static com.fincity.saas.core.jooq.tables.CoreTokens.CORE_TOKENS;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.saas.core.dto.CoreToken;
import com.fincity.saas.core.jooq.tables.records.CoreTokensRecord;

import reactor.core.publisher.Mono;

@Service
public class CoreTokenDAO extends AbstractDAO<CoreTokensRecord, ULong, CoreToken> {

	protected CoreTokenDAO() {
		super(CoreToken.class, CORE_TOKENS, CORE_TOKENS.ID);
	}

	public Mono<CoreToken> getActiveCoreToken(ULong userId, String clientCode, String appCode, String connectionName) {

		return Mono.from(
				this.dslContext.selectFrom(CORE_TOKENS)
						.where(CORE_TOKENS.USER_ID.equal(userId)
								.and(CORE_TOKENS.CLIENT_CODE.eq(clientCode))
								.and(CORE_TOKENS.APP_CODE.eq(appCode))
								.and(CORE_TOKENS.CONNECTION_NAME.eq(connectionName))
								.and(CORE_TOKENS.IS_REVOKED.eq((byte) 0))
						)
		).map(coreTokensRecord -> coreTokensRecord.into(CoreToken.class));
	}

	public Mono<CoreToken> getActiveCoreToken(String clientCode, String appCode, String connectionName) {

		return Mono.from(
				this.dslContext.selectFrom(CORE_TOKENS)
						.where(CORE_TOKENS.CLIENT_CODE.eq(clientCode)
								.and(CORE_TOKENS.APP_CODE.eq(appCode))
								.and(CORE_TOKENS.CONNECTION_NAME.eq(connectionName))
								.and(CORE_TOKENS.IS_REVOKED.eq((byte) 0))
						)
		).map(coreTokensRecord -> coreTokensRecord.into(CoreToken.class));
	}

	public Mono<String> getActiveToken(String clientCode, String appCode, String connectionName) {

		return Mono.from(
				this.dslContext.select(CORE_TOKENS.TOKEN)
						.where(CORE_TOKENS.CLIENT_CODE.eq(clientCode)
								.and(CORE_TOKENS.APP_CODE.eq(appCode))
								.and(CORE_TOKENS.CONNECTION_NAME.eq(connectionName))
								.and(CORE_TOKENS.IS_REVOKED.eq((byte) 0))
						)
		).map(coreTokensRecord -> coreTokensRecord.into(String.class));
	}

}
