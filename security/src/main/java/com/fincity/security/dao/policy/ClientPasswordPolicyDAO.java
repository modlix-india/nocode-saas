package com.fincity.security.dao.policy;

import static com.fincity.security.jooq.tables.SecurityClientPasswordPolicy.SECURITY_CLIENT_PASSWORD_POLICY;

import org.jooq.Field;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.security.dto.policy.ClientPasswordPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientPasswordPolicyRecord;

@Component
public class ClientPasswordPolicyDAO
		extends AbstractPolicyDao<SecurityClientPasswordPolicyRecord, ClientPasswordPolicy> {

	public ClientPasswordPolicyDAO() {
		super(ClientPasswordPolicy.class, SECURITY_CLIENT_PASSWORD_POLICY, SECURITY_CLIENT_PASSWORD_POLICY.ID);
	}

	@Override
	protected Field<ULong> getClientIDField() {
		return SECURITY_CLIENT_PASSWORD_POLICY.CLIENT_ID;
	}

	@Override
	protected Field<ULong> getAppIdField() {
		return SECURITY_CLIENT_PASSWORD_POLICY.APP_ID;
	}
}
