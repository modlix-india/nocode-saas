package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityIntegration.SECURITY_INTEGRATION;

import org.jooq.Field;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.security.jooq.tables.records.SecurityIntegrationRecord;
import com.fincity.security.model.Integration;

@Service
public class IntegrationDao extends AbstractClientCheckDAO<SecurityIntegrationRecord, ULong, Integration> {

  protected IntegrationDao() {
    super(Integration.class, SECURITY_INTEGRATION, SECURITY_INTEGRATION.ID);
  }

  @Override
  protected Field<ULong> getClientIDField() {
    return SECURITY_INTEGRATION.CLIENT_ID;
  }

}
