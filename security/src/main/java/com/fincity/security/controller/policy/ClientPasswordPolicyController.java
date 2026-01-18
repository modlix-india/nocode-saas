package com.fincity.security.controller.policy;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.security.dao.policy.ClientPasswordPolicyDAO;
import com.fincity.security.dto.policy.ClientPasswordPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientPasswordPolicyRecord;
import com.fincity.security.service.policy.ClientPasswordPolicyService;

@RestController
@RequestMapping("api/security/clientPasswordPolicy")
public class ClientPasswordPolicyController extends
		AbstractPolicyController<SecurityClientPasswordPolicyRecord, ClientPasswordPolicy, ClientPasswordPolicyDAO, ClientPasswordPolicyService> {

}
