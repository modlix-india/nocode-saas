package com.fincity.security.dto.appregistration;

import org.jooq.types.ULong;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class AppRegistrationDesignation extends AbstractAppRegistration {

    private String name;
    private String description;
    private ULong departmentId;
    private ULong parentDesignationId;
    private ULong nextDesignationId;

    private AppRegistrationDepartment department;
    private AppRegistrationDesignation parentDesignation;
    private AppRegistrationDesignation nextDesignation;
}
