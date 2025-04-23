package com.fincity.security.dto.appregistration;

import org.jooq.types.ULong;

import com.fincity.security.dto.App;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class AppRegistrationAccess extends AbstractAppRegistration {

    private ULong allowAppId;
    private boolean writeAccess;

    // Extras required for UI
    private App allowApp;
}
