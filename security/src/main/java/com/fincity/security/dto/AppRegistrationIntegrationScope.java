package com.fincity.security.dto;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.security.model.Integration;
import com.fincity.security.model.IntegrationScope;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class AppRegistrationIntegrationScope extends AbstractDTO<ULong, ULong> {

  private static final long serialVersionUID = -3749285736483726589L;

  private ULong id;
  private ULong clientId;
  private ULong appId;
  private ULong integrationId;
  private ULong integrationScopeId;
  private Integration integration;
  private IntegrationScope integrationScope;

  private App app;
  private Client client;
}
