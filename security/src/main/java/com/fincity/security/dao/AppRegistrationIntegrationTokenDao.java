package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityApp.SECURITY_APP;
import static com.fincity.security.jooq.tables.SecurityAppRegIntegration.SECURITY_APP_REG_INTEGRATION;
import static com.fincity.security.jooq.tables.SecurityAppRegIntegrationTokens.SECURITY_APP_REG_INTEGRATION_TOKENS;
import static com.fincity.security.jooq.tables.SecurityClient.SECURITY_CLIENT;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.security.dto.AppRegistrationIntegrationToken;
import com.fincity.security.jooq.tables.records.SecurityAppRegIntegrationTokensRecord;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AppRegistrationIntegrationTokenDao
    extends AbstractDAO<SecurityAppRegIntegrationTokensRecord, ULong, AppRegistrationIntegrationToken> {

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

}
