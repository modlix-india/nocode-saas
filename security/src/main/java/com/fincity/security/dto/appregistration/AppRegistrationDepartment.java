package com.fincity.security.dto.appregistration;

import org.jooq.types.ULong;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class AppRegistrationDepartment extends AbstractAppRegistration {

    private String name;
    private String description;
    private ULong parentDepartmentId;

    private AppRegistrationDepartment parentDepartment;
}
