package com.fincity.security.controller;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.DepartmentDAO;
import com.fincity.security.dao.RoleV2DAO;
import com.fincity.security.dto.Department;
import com.fincity.security.dto.RoleV2;
import com.fincity.security.jooq.tables.records.SecurityDepartmentRecord;
import com.fincity.security.jooq.tables.records.SecurityV2RoleRecord;
import com.fincity.security.service.DepartmentService;
import com.fincity.security.service.ProfileService;
import com.fincity.security.service.RoleV2Service;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Stream;

@RestController
@RequestMapping("api/security/departments")
public class DepartmentController
        extends AbstractJOOQUpdatableDataController<SecurityDepartmentRecord, ULong, Department, DepartmentDAO, DepartmentService> {
}
