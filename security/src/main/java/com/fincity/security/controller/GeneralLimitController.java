package com.fincity.security.controller;

import org.jooq.types.ULong;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.LimitAccessDAO;
import com.fincity.security.dto.LimitAccess;
import com.fincity.security.jooq.tables.records.SecurityAppLimitationsRecord;
import com.fincity.security.service.LimitAccessService;

@RestController
@RequestMapping("api/security/general/limits")
public class GeneralLimitController
        extends
        AbstractJOOQUpdatableDataController<SecurityAppLimitationsRecord, ULong, LimitAccess, LimitAccessDAO, LimitAccessService> {

}
