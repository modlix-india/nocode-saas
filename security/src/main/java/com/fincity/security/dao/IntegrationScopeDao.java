package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityIntegrationScopes.SECURITY_INTEGRATION_SCOPES;

import org.jooq.Field;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.security.jooq.tables.records.SecurityIntegrationScopesRecord;
import com.fincity.security.model.IntegrationScope;

@Service
public class IntegrationScopeDao
    extends AbstractClientCheckDAO<SecurityIntegrationScopesRecord, ULong, IntegrationScope> {

  protected IntegrationScopeDao() {
    super(IntegrationScope.class, SECURITY_INTEGRATION_SCOPES, SECURITY_INTEGRATION_SCOPES.ID);
  }

  @Override
  protected Field<ULong> getClientIDField() {
    return SECURITY_INTEGRATION_SCOPES.CLIENT_ID;
  }

}
