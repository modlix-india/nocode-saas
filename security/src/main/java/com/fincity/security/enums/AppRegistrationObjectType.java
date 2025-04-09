package com.fincity.security.enums;

import static com.fincity.security.jooq.tables.SecurityAppRegAccess.*;
import static com.fincity.security.jooq.tables.SecurityAppRegDepartment.*;
import static com.fincity.security.jooq.tables.SecurityAppRegDesignation.*;
import static com.fincity.security.jooq.tables.SecurityAppRegFileAccess.*;
import static com.fincity.security.jooq.tables.SecurityAppRegProfileRestriction.*;
import static com.fincity.security.jooq.tables.SecurityAppRegUserDesignation.*;
import static com.fincity.security.jooq.tables.SecurityAppRegUserProfile.*;
import static com.fincity.security.jooq.tables.SecurityAppRegUserRoleV2.*;

import java.util.Arrays;

import org.jooq.Table;

import com.fincity.security.dto.appregistration.AbstractAppRegistration;
import com.fincity.security.dto.appregistration.AppRegistrationAccess;
import com.fincity.security.dto.appregistration.AppRegistrationDepartment;
import com.fincity.security.dto.appregistration.AppRegistrationDesignation;
import com.fincity.security.dto.appregistration.AppRegistrationFileAccess;
import com.fincity.security.dto.appregistration.AppRegistrationProfileRestriction;
import com.fincity.security.dto.appregistration.AppRegistrationUserDesignation;
import com.fincity.security.dto.appregistration.AppRegistrationUserProfile;
import com.fincity.security.dto.appregistration.AppRegistrationUserRole;
import com.fincity.security.service.AppService;
import com.fincity.security.service.ProfileService;
import com.fincity.security.service.RoleV2Service;
import com.fincity.security.service.appregistration.AppRegistrationServiceV2;
import com.fincity.security.service.appregistration.IAppRegistrationHelperService;

public enum AppRegistrationObjectType {
    APPLICATION_ACCESS("appAccess", SECURITY_APP_REG_ACCESS, AppRegistrationAccess.class,
            new ExtraValues("allowAppId", "allowApp", AppService.class)),
    DEPARTMENT("department", SECURITY_APP_REG_DEPARTMENT, AppRegistrationDepartment.class,
            new ExtraValues("parentDepartmentId", "parentDepartment", AppRegistrationServiceV2.class)),
    DESIGNATION("designation", SECURITY_APP_REG_DESIGNATION, AppRegistrationDesignation.class,
            new ExtraValues("parentDesignationId", "parentDesignation", AppRegistrationServiceV2.class),
            new ExtraValues("nextDesignationId", "nextDesignation", AppRegistrationServiceV2.class),
            new ExtraValues("departmentId", "department", AppRegistrationServiceV2.class)),
    FILE_ACCESS("fileAccess", SECURITY_APP_REG_FILE_ACCESS, AppRegistrationFileAccess.class),
    PROFILE_RESTRICTION("profileRestriction", SECURITY_APP_REG_PROFILE_RESTRICTION,
            AppRegistrationProfileRestriction.class, new ExtraValues("profileId", "profile", ProfileService.class)),
    USER_DESIGNATION("userDesignation", SECURITY_APP_REG_USER_DESIGNATION, AppRegistrationUserDesignation.class,
            new ExtraValues("designationId", "designation", AppRegistrationServiceV2.class)),
    USER_PROFILE("userProfile", SECURITY_APP_REG_USER_PROFILE, AppRegistrationUserProfile.class,
            new ExtraValues("profileId", "profile", ProfileService.class)),
    USER_ROLE("userRole", SECURITY_APP_REG_USER_ROLE_V2, AppRegistrationUserRole.class,
            new ExtraValues("roleId", "role", RoleV2Service.class));

    public final String urlPart;
    public final Table<?> table;
    public final Class<? extends AbstractAppRegistration> pojoClass;
    public final ExtraValues[] extraValues;

    private AppRegistrationObjectType(String urlPart, Table<?> table,
            Class<? extends AbstractAppRegistration> pojoClass, ExtraValues... extraValues) {
        this.urlPart = urlPart;
        this.table = table;
        this.pojoClass = pojoClass;
        this.extraValues = extraValues;
    }

    public static AppRegistrationObjectType fromUrlPart(String urlPart) {
        return Arrays.stream(values())
                .filter(v -> v.urlPart.equals(urlPart))
                .findFirst()
                .orElse(null);
    }

    public static record ExtraValues(String idFieldName, String objectFieldName,
            Class<? extends IAppRegistrationHelperService> helperServiceClass) {
    }
}