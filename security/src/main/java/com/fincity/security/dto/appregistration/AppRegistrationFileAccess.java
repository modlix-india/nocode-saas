package com.fincity.security.dto.appregistration;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class AppRegistrationFileAccess extends AbstractAppRegistration {

    private String resourceType;
    private String accessName;
    private boolean writeAccess;
    private String path;
    private boolean allowSubPathAccess;
}
