package com.fincity.security.controller;

import org.jooq.types.ULong;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.IntegrationDao;
import com.fincity.security.jooq.tables.records.SecurityIntegrationRecord;
import com.fincity.security.model.Integration;
import com.fincity.security.service.appintegration.IntegrationService;

@RestController
@RequestMapping("api/security/integrations")
public class IntegrationController extends
                AbstractJOOQUpdatableDataController<SecurityIntegrationRecord, ULong, Integration, IntegrationDao, IntegrationService> {

}
