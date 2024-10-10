package com.fincity.security.dto;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.jooq.enums.SecurityAppRegIntegrationPlatform;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class AppRegistrationIntegration extends AbstractUpdatableDTO<ULong, ULong> {

  private static final long serialVersionUID = 987263832864832109L;

  private ULong clientId;
  private ULong appId;
  private SecurityAppRegIntegrationPlatform platform;
  private String intgId;
  private String intgSecret;
  private String loginUri;
}
