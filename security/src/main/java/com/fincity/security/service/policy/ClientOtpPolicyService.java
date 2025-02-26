package com.fincity.security.service.policy;

import org.jooq.types.ULong;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.policy.ClientOtpPolicyDAO;
import com.fincity.security.dto.policy.ClientOtpPolicy;
import com.fincity.security.jooq.enums.SecurityClientOtpPolicyTargetType;
import com.fincity.security.jooq.tables.records.SecurityClientOtpPolicyRecord;
import com.fincity.security.model.AuthenticationPasswordType;
import com.fincity.security.service.SecurityMessageResourceService;

import reactor.core.publisher.Mono;

@Service
public class ClientOtpPolicyService
		extends AbstractPolicyService<SecurityClientOtpPolicyRecord, ClientOtpPolicy, ClientOtpPolicyDAO>
		implements IPolicyService<ClientOtpPolicy> {

	private static final String CLIENT_OTP_POLICY = "client_otp_policy";

	private static final String CACHE_NAME_CLIENT_OTP_POLICY = "clientOtpPolicy";

	protected ClientOtpPolicyService(SecurityMessageResourceService securityMessageResourceService,
			CacheService cacheService) {
		super(securityMessageResourceService, cacheService);
	}

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
						.setLength((short) 4)
						.setResendSameOtp(false)
						.setNoResendAttempts((short) 3)
						.setExpireInterval(5L)
						.setClientId(ULongUtil.valueOf(0))
						.setAppId(ULongUtil.valueOf(0))
						.setNoFailedAttempts((short) 3)
						.setUserLockTimeMin(15L)
						.setId(DEFAULT_POLICY_ID));
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
					e.setNoFailedAttempts(
							entity.getNoFailedAttempts() != null ? entity.getNoFailedAttempts() : (short) 3);
					e.setUserLockTimeMin(entity.getUserLockTimeMin() != null ? entity.getUserLockTimeMin() : 15L);
					return e;
				});
	}

	@Override
	public AuthenticationPasswordType getAuthenticationPasswordType() {
		return AuthenticationPasswordType.OTP;
	}

	@Override
	public Mono<Boolean> checkAllConditions(ULong clientId, ULong appId, ULong userId, String otp) {
		return Mono.just(Boolean.TRUE);
	}

	@Override
	public Mono<Boolean> checkAllConditions(ClientOtpPolicy policy, ULong userId, String password) {
		return Mono.just(Boolean.TRUE);
	}
}
