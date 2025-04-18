package com.fincity.security.dto.appregistration;

import org.jooq.types.ULong;

import com.fincity.security.dto.RoleV2;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class AppRegistrationUserRole extends AbstractAppRegistration {

    private ULong roleId;

    private RoleV2 role;
}
