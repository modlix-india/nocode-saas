package com.fincity.security.service.appintegration;

import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.security.dao.IntegrationDao;
import com.fincity.security.jooq.tables.records.SecurityIntegrationRecord;
import com.fincity.security.model.Integration;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.SecurityMessageResourceService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class IntegrationService
    extends AbstractJOOQUpdatableDataService<SecurityIntegrationRecord, ULong, Integration, IntegrationDao> {

  private ClientService clientService;
  private SecurityMessageResourceService securityMessageResourceService;

  IntegrationService(ClientService clientService, SecurityMessageResourceService securityMessageResourceService) {
    this.clientService = clientService;
    this.securityMessageResourceService = securityMessageResourceService;
  }

  @Override
  protected Mono<ULong> getLoggedInUserId() {

    return SecurityContextUtil.getUsersContextUser().map(ContextUser::getId).map(ULong::valueOf);
  }

  @PreAuthorize("hasAuthority('Authorities.Integration_CREATE')")
  @Override
  public Mono<Integration> create(Integration entity) {

    return SecurityContextUtil.getUsersContextAuthentication().flatMap(ca -> {

      if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode())) {
        return super.create(entity);
      }

      if (entity.getClientId() == null)
        return this.securityMessageResourceService.throwMessage(
            msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
            SecurityMessageResourceService.MANDATORY_CLIENT_CODE);

      ULong userClientId = ULongUtil.valueOf(ca.getUser().getClientId());

      if (entity.getClientId() == null || userClientId.equals(entity.getClientId())) {
        entity.setClientId(userClientId);
        return super.create(entity);
      }

      return clientService.isBeingManagedBy(userClientId, entity.getClientId()).flatMap(managed -> {
        if (managed.booleanValue())
          return super.create(entity);

        return Mono.empty();
      }).switchIfEmpty(Mono.defer(
          () -> securityMessageResourceService.getMessage(SecurityMessageResourceService.FORBIDDEN_CREATE).flatMap(
              msg -> Mono.error(new GenericException(HttpStatus.FORBIDDEN, StringFormatter.format(msg, "User"))))));
    });

  }

  @PreAuthorize("hasAuthority('Authorities.Integration_READ')")
  @Override
  public Mono<Integration> read(ULong id) {
    return super.read(id);
  }

  @PreAuthorize("hasAuthority('Authorities.Integration_READ')")
  public Flux<Integration> readFilterWithReadPermission(AbstractCondition cond) {
    return super.readAllFilter(cond);
  }

  @PreAuthorize("hasAuthority('Authorities.Integration_READ')")
  @Override
  public Mono<Page<Integration>> readPageFilter(Pageable pageable, AbstractCondition condition) {
    return super.readPageFilter(pageable, condition);
  }

  @Override
  protected Mono<Integration> updatableEntity(Integration entity) {
    return Mono.just(entity);
  }

  @Override
  protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {
    return Mono.just(fields);
  }
}
