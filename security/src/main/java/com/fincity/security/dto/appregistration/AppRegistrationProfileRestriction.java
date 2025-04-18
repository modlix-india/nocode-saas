package com.fincity.security.dto.appregistration;

import org.jooq.types.ULong;

import com.fincity.security.dto.Profile;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class AppRegistrationProfileRestriction extends AbstractAppRegistration {

    private ULong profileId;

    private Profile profile;
}
