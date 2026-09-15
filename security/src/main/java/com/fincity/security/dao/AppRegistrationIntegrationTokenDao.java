package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityApp.SECURITY_APP;
import static com.fincity.security.jooq.tables.SecurityAppRegIntegration.SECURITY_APP_REG_INTEGRATION;
import static com.fincity.security.jooq.tables.SecurityAppRegIntegrationTokens.SECURITY_APP_REG_INTEGRATION_TOKENS;
import static com.fincity.security.jooq.tables.SecurityClient.SECURITY_CLIENT;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.AppRegistrationIntegrationToken;
import com.fincity.security.jooq.tables.records.SecurityAppRegIntegrationTokensRecord;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AppRegistrationIntegrationTokenDao
    extends AbstractUpdatableDAO<SecurityAppRegIntegrationTokensRecord, ULong, AppRegistrationIntegrationToken> {

  protected AppRegistrationIntegrationTokenDao() {
    super(AppRegistrationIntegrationToken.class, SECURITY_APP_REG_INTEGRATION_TOKENS,
        SECURITY_APP_REG_INTEGRATION_TOKENS.ID);
  }

  public Mono<String> getTokensOfUserId(String appCode, String clientCode, ULong userId) {

    return Mono.from(
        this.dslContext.select(SECURITY_APP_REG_INTEGRATION_TOKENS.TOKEN).from(SECURITY_APP_REG_INTEGRATION_TOKENS)
            .leftJoin(SECURITY_APP_REG_INTEGRATION)
            .on(SECURITY_APP_REG_INTEGRATION_TOKENS.INTEGRATION_ID.eq(SECURITY_APP_REG_INTEGRATION.ID))
            .leftJoin(SECURITY_APP).on(SECURITY_APP_REG_INTEGRATION.APP_ID.eq(SECURITY_APP.ID))
            .leftJoin(SECURITY_CLIENT).on(SECURITY_APP.CLIENT_ID.eq(SECURITY_CLIENT.ID))
            .where(
                DSL.and(
                    SECURITY_APP_REG_INTEGRATION_TOKENS.CREATED_BY.eq(userId),
                    SECURITY_APP.APP_CODE.eq(appCode),
                    SECURITY_CLIENT.CODE.eq(clientCode))))
        .map(Record1::value1);
  }

	public Mono<AppRegistrationIntegrationToken> findByState(String state) {

		return Mono
				.from(this.dslContext.selectFrom(SECURITY_APP_REG_INTEGRATION_TOKENS)
						.where(SECURITY_APP_REG_INTEGRATION_TOKENS.STATE.eq(state)).limit(1))
				.map(e -> e.into(AppRegistrationIntegrationToken.class));
	}

	/**
	 * Clear the state so it cannot be looked up again, and report whether this call was the one
	 * that cleared it.
	 *
	 * The column carries a UNIQUE key, so this single statement is also the lock: two callers
	 * racing the same state both run the update, and only one of them can match a row and see a
	 * count of 1. That is what makes a single-use state single-use, rather than the caller
	 * checking first and hoping.
	 *
	 * The row itself stays, with its provider tokens and its audit columns.
	 */
	public Mono<Boolean> consumeState(String state) {

		return Mono
				.from(this.dslContext.update(SECURITY_APP_REG_INTEGRATION_TOKENS)
						.setNull(SECURITY_APP_REG_INTEGRATION_TOKENS.STATE)
						.where(SECURITY_APP_REG_INTEGRATION_TOKENS.STATE.eq(state)))
				.map(count -> count == 1)
				.defaultIfEmpty(Boolean.FALSE);
	}

}
