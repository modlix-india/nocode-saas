package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityClientPasswordPolicy.SECURITY_CLIENT_PASSWORD_POLICY;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.security.dto.ClientPasswordPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientPasswordPolicyRecord;

@Component
public class ClientPasswordPolicyDAO
        extends AbstractUpdatableDAO<SecurityClientPasswordPolicyRecord, ULong, ClientPasswordPolicy> {

	public ClientPasswordPolicyDAO() {
		super(ClientPasswordPolicy.class, SECURITY_CLIENT_PASSWORD_POLICY, SECURITY_CLIENT_PASSWORD_POLICY.ID);
	}
}
