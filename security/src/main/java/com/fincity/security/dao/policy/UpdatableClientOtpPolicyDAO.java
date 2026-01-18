package com.fincity.security.dao.policy;

import static com.fincity.security.jooq.tables.SecurityClientOtpPolicy.SECURITY_CLIENT_OTP_POLICY;

import org.jooq.Field;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.security.dto.policy.ClientOtpPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientOtpPolicyRecord;

@Component
public class UpdatableClientOtpPolicyDAO extends AbstractUpdatablePolicyDao<SecurityClientOtpPolicyRecord, ClientOtpPolicy> {

	public UpdatableClientOtpPolicyDAO() {
		super(ClientOtpPolicy.class, SECURITY_CLIENT_OTP_POLICY, SECURITY_CLIENT_OTP_POLICY.ID);
	}

	@Override
	public Field<ULong> getClientIDField() {
		return SECURITY_CLIENT_OTP_POLICY.CLIENT_ID;
	}

	@Override
	protected Field<ULong> getAppIdField() {
		return SECURITY_CLIENT_OTP_POLICY.APP_ID;
	}
}
