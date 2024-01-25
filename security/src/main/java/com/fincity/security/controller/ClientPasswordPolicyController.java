package com.fincity.security.controller;

import org.jooq.types.ULong;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.ClientPasswordPolicyDAO;
import com.fincity.security.dto.ClientPasswordPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientPasswordPolicyRecord;
import com.fincity.security.service.ClientPasswordPolicyService;

@RestController
@RequestMapping("api/security/clientPasswordPolicy")
public class ClientPasswordPolicyController extends
        AbstractJOOQUpdatableDataController<SecurityClientPasswordPolicyRecord, ULong, ClientPasswordPolicy, ClientPasswordPolicyDAO, ClientPasswordPolicyService> {

}
