package com.fincity.security.controller;

import org.jooq.types.ULong;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.IntegrationScopeDao;
import com.fincity.security.jooq.tables.records.SecurityIntegrationScopesRecord;
import com.fincity.security.model.IntegrationScope;
import com.fincity.security.service.appintegration.IntegrationScopeService;

@RestController
@RequestMapping("api/security/integration-scopes")
public class IntegrationScopeController extends
    AbstractJOOQUpdatableDataController<SecurityIntegrationScopesRecord, ULong, IntegrationScope, IntegrationScopeDao, IntegrationScopeService> {

}
