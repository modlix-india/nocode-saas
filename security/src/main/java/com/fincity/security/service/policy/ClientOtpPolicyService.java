package com.fincity.security.service.policy;

import org.jooq.types.ULong;
import org.jooq.types.UShort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.security.dao.policy.ClientOtpPolicyDAO;
import com.fincity.security.dto.policy.ClientOtpPolicy;
import com.fincity.security.jooq.enums.SecurityClientOtpPolicyTargetType;
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
	protected Mono<ClientOtpPolicy> getDefaultPolicy() {
		return Mono.just(
				(ClientOtpPolicy) new ClientOtpPolicy()
						.setTargetType(SecurityClientOtpPolicyTargetType.EMAIL)
						.setConstant(false)
						.setNumeric(true)
						.setAlphanumeric(false)
						.setLength(UShort.valueOf(4))
						.setResendSameOtp(false)
						.setNoResendAttempts(UShort.valueOf(3))
						.setExpireInterval(ULong.valueOf(5))
						.setClientId(ULong.valueOf(0))
						.setAppId(ULong.valueOf(0))
						.setNoFailedAttempts(UShort.valueOf(3))
						.setUserLockTimeMin(ULong.valueOf(30)));
	}

	@PreAuthorize("hasAuthority('Authorities.Client_Password_Policy_READ')")
	@Override
	protected Mono<ClientOtpPolicy> updatableEntity(ClientOtpPolicy entity) {
		return this.read(entity.getId())
				.map(e -> {
					e.setNoFailedAttempts(entity.getNoFailedAttempts());
					e.setTargetType(entity.getTargetType());
					e.setConstant(entity.isConstant());
					e.setConstantValue(entity.getConstantValue());
					e.setNumeric(entity.isNumeric());
					e.setAlphanumeric(entity.isAlphanumeric());
					e.setLength(entity.getLength());
					e.setExpireInterval(entity.getExpireInterval());
					e.setResendSameOtp(entity.isResendSameOtp());
					e.setNoResendAttempts(entity.getNoResendAttempts());
					return e;
				});
	}

	@Override
	public AuthenticationPasswordType getAuthenticationPasswordType() {
		return AuthenticationPasswordType.OTP;
	}

	@Override
	public Mono<Boolean> checkAllConditions(ULong clientId, ULong appId, ULong userId, String password) {
		return null;
	}
}
