package com.fincity.security.dto.appintegration;

import java.time.LocalDateTime;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Accessors(chain = true)
public class IntegrationTokenObject extends AbstractDTO<ULong, ULong> {

  private static final long serialVersionUID = -8765432109876543210L;

  private ULong id;
  private ULong integrationId;
  private ULong userId;
  private ULong clientId;
  private String token;
  private String refreshToken;
  private LocalDateTime expiresAt;
}
