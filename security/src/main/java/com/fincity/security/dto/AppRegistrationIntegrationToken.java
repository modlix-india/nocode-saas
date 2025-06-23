package com.fincity.security.dto;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.Map;

import org.jooq.types.ULong;

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
  private Map<String, Object> requestParam;
  private String token;
  private String refreshToken;
  private LocalDateTime expiresAt;
  private String username;
  private Map<String, Object> tokenMetadata;
  private Map<String, Object> userMetadata;
}
