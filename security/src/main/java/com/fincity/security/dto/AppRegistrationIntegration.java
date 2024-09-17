package com.fincity.security.dto;

import java.util.List;

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
public class AppRegistrationIntegration extends AbstractDTO<ULong, ULong> {

  private static final long serialVersionUID = -3752854905564766380L;

  private ULong id;
  private ULong clientId;
  private ULong appId;
  private ULong integrationId;
  private Integration integration;
  private List<IntegrationScope> integrationScopes;

  private Client client;
  private App app;

}
