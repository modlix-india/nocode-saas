package com.fincity.security.controller;

import org.jooq.types.ULong;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQDataController;
import com.fincity.security.dao.PermissionDAO;
import com.fincity.security.dto.Permission;
import com.fincity.security.jooq.tables.records.SecurityPermissionRecord;
import com.fincity.security.service.PermissionService;

@RestController
@RequestMapping("api/security/permissions")
public class PermissionController extends
        AbstractJOOQDataController<SecurityPermissionRecord, ULong, Permission, PermissionDAO, PermissionService> {

}
