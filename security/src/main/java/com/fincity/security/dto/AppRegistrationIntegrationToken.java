package com.fincity.security.dto;

import java.io.Serial;
import java.time.LocalDateTime;

import org.jooq.types.ULong;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Accessors(chain = true)
public class AppRegistrationIntegrationToken extends AbstractUpdatableDTO<ULong, ULong> {

  @Serial
  private static final long serialVersionUID = -8765432109876543210L;

  private ULong integrationId;
  private String authCode;
  private String state;
  private String token;
  private String refreshToken;
  private LocalDateTime expiresAt;
  private String username;
  private JsonNode tokenMetadata;
  private JsonNode userMetadata;
}
