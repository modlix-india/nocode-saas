package com.fincity.security.controller.appregistration;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQDataController;
import com.fincity.security.dao.AppRegistrationIntegrationDAO;
import com.fincity.security.dto.AppRegistrationIntegration;
import com.fincity.security.jooq.tables.records.SecurityAppRegIntegrationRecord;
import com.fincity.security.service.appregistration.AppRegistrationIntegrationService;
import org.jooq.types.ULong;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/security/appRegIntegration")
public class AppRegistrationIntegrationController extends
        AbstractJOOQDataController<SecurityAppRegIntegrationRecord, ULong, AppRegistrationIntegration, AppRegistrationIntegrationDAO, AppRegistrationIntegrationService> {

}
