package com.fincity.security.service.appintegration;

import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.security.dao.IntegrationScopeDao;
import com.fincity.security.jooq.tables.records.SecurityIntegrationScopesRecord;
import com.fincity.security.model.IntegrationScope;

import reactor.core.publisher.Mono;

@Service
public class IntegrationScopeService extends
    AbstractJOOQUpdatableDataService<SecurityIntegrationScopesRecord, ULong, IntegrationScope, IntegrationScopeDao> {

  @Override
  protected Mono<ULong> getLoggedInUserId() {

    return SecurityContextUtil.getUsersContextUser().map(ContextUser::getId).map(ULong::valueOf);
  }

  @Override
  protected Mono<IntegrationScope> updatableEntity(IntegrationScope entity) {
    return Mono.just(entity);
  }

  @Override
  protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {
    return Mono.just(fields);
  }
}
