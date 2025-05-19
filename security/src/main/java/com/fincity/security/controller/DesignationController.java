package com.fincity.security.controller;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.DesignationDAO;
import com.fincity.security.dto.Designation;
import com.fincity.security.jooq.tables.records.SecurityDesignationRecord;
import com.fincity.security.service.DesignationService;
import org.jooq.types.ULong;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/security/designations")
public class DesignationController
        extends AbstractJOOQUpdatableDataController<SecurityDesignationRecord, ULong, Designation, DesignationDAO, DesignationService> {
}
