package com.fincity.security.controller.policy;

import org.jooq.types.ULong;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.policy.ClientOtpPolicyDAO;
import com.fincity.security.dto.policy.ClientOtpPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientOtpPolicyRecord;
import com.fincity.security.service.policy.ClientOtpPolicyService;

@RestController
@RequestMapping("api/security/clientOtpPolicy")
public class ClientOtpPolicyController extends
		AbstractJOOQUpdatableDataController<SecurityClientOtpPolicyRecord, ULong, ClientOtpPolicy, ClientOtpPolicyDAO, ClientOtpPolicyService> {

}
