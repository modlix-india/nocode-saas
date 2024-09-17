package com.fincity.security.model;

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
public class Integration extends AbstractUpdatableDTO<ULong, ULong> {

  private static final long serialVersionUID = -1234567890123456789L;

  private ULong id;
  private ULong clientId;
  private String name;
  private String platform;
  private String description;
}
