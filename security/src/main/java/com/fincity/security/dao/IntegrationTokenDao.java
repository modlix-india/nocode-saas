package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityIntegrationTokens.SECURITY_INTEGRATION_TOKENS;

import org.jooq.Record1;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.security.dto.IntegrationTokenObject;
import com.fincity.security.jooq.tables.records.SecurityIntegrationTokensRecord;

import reactor.core.publisher.Flux;

@Service
public class IntegrationTokenDao extends AbstractDAO<SecurityIntegrationTokensRecord, ULong, IntegrationTokenObject> {

  protected IntegrationTokenDao() {
    super(IntegrationTokenObject.class, SECURITY_INTEGRATION_TOKENS, SECURITY_INTEGRATION_TOKENS.ID);
  }

  public Flux<String> getTokensOfUserId(ULong userId) {

    return Flux.from(this.dslContext.select(SECURITY_INTEGRATION_TOKENS.TOKEN).from(SECURITY_INTEGRATION_TOKENS)
        .where(SECURITY_INTEGRATION_TOKENS.USER_ID.eq(userId))).map(Record1::value1);
  }

}
