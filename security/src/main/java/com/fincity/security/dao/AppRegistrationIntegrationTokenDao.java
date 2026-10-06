package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityAppRegIntegrationTokens.SECURITY_APP_REG_INTEGRATION_TOKENS;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.AppRegistrationIntegrationToken;
import com.fincity.security.jooq.tables.records.SecurityAppRegIntegrationTokensRecord;
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
	 * The row itself stays, with the verified username and its audit columns. It holds no provider
	 * code or token: those are never stored.
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
