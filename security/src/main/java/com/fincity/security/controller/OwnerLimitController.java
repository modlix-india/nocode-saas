package com.fincity.security.controller;

import org.jooq.types.ULong;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.LimitOwnerAccessDAO;
import com.fincity.security.dto.LimitAccess;
import com.fincity.security.jooq.tables.records.SecurityAppOwnerLimitationsRecord;
import com.fincity.security.service.LimitOwnerAccessService;

@RestController
@RequestMapping("api/security/owner/limits")
public class OwnerLimitController extends
        AbstractJOOQUpdatableDataController<SecurityAppOwnerLimitationsRecord, ULong, LimitAccess, LimitOwnerAccessDAO, LimitOwnerAccessService> {

}
