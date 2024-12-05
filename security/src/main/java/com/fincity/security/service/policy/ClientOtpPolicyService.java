package com.fincity.security.service.policy;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.security.dao.policy.ClientOtpPolicyDAO;
import com.fincity.security.dto.policy.ClientOtpPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientOtpPolicyRecord;
import com.fincity.security.model.AuthenticationPasswordType;

import reactor.core.publisher.Mono;

@Service
public class ClientOtpPolicyService extends AbstractPolicyService<SecurityClientOtpPolicyRecord, ClientOtpPolicy, ClientOtpPolicyDAO>
		implements IPolicyService<ClientOtpPolicy> {

	private static final String CLIENT_OTP_POLICY = "client_otp_policy";

	private static final String CACHE_NAME_CLIENT_OTP_POLICY = "clientOtpPolicy";

	@Override
	public String getPolicyName() {
		return CLIENT_OTP_POLICY;
	}

	@Override
	public String getPolicyCacheName() {
		return CACHE_NAME_CLIENT_OTP_POLICY;
	}

	@Override
	protected Mono<ClientOtpPolicy> updatableEntity(ClientOtpPolicy entity) {
		return this.read(entity.getId())
				.map(e -> {
					e.setNoFailedAttempts(entity.getNoFailedAttempts());
					e.setNumeric(entity.isNumeric());
					e.setAlphanumeric(entity.isAlphanumeric());
					e.setLength(entity.getLength());
					e.setExpireInterval(entity.getExpireInterval());
					return e;
				});
	}

	@Override
	public AuthenticationPasswordType getAuthenticationPasswordType() {
		return AuthenticationPasswordType.OTP;
	}

	@Override
	public Mono<Boolean> checkAllConditions(ULong clientId, ULong appId, String password) {
		return null;
	}
}
