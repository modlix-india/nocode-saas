package com.fincity.security.dto.appregistration;

import org.jooq.types.ULong;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class AppRegistrationUserDesignation extends AbstractAppRegistration {

    private ULong designationId;

    private AppRegistrationDesignation designation;
}
