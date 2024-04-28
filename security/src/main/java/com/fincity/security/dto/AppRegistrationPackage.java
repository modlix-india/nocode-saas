package com.fincity.security.dto;

import org.jooq.types.ULong;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class AppRegistrationPackage extends AbstractAppRegistration {

    private ULong packageId;

    private Package packageDetails;
}
