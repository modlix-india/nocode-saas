package com.fincity.security.controller;

import org.jooq.types.ULong;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.RoleV2DAO;
import com.fincity.security.dto.RoleV2;
import com.fincity.security.jooq.tables.records.SecurityV2RoleRecord;
import com.fincity.security.service.RoleV2Service;

@RestController
@RequestMapping("api/security/rolev2")
public class RoleV2Controller
        extends AbstractJOOQUpdatableDataController<SecurityV2RoleRecord, ULong, RoleV2, RoleV2DAO, RoleV2Service> {

}
