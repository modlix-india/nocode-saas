package com.fincity.security.controller.policy;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.security.dao.policy.ClientPinPolicyDAO;
import com.fincity.security.dto.policy.ClientPinPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientPinPolicyRecord;
import com.fincity.security.service.policy.ClientPinPolicyService;

@RestController
@RequestMapping("api/security/clientPinPolicy")
public class ClientPinPolicyController extends
		AbstractPolicyController<SecurityClientPinPolicyRecord, ClientPinPolicy, ClientPinPolicyDAO, ClientPinPolicyService> {

}
