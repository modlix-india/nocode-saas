package com.fincity.security.dto;

import org.jooq.types.ULong;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class AppRegistrationRole extends AbstractAppRegistration {

    private ULong roleId;

    private Role role;
}
