package com.fincity.security.service.policy;

import org.jooq.types.ULong;
import org.jooq.types.UShort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.security.dao.policy.ClientPinPolicyDAO;
import com.fincity.security.dto.policy.ClientPinPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientPinPolicyRecord;
import com.fincity.security.model.AuthenticationPasswordType;

import reactor.core.publisher.Mono;

@Service
public class ClientPinPolicyService extends AbstractPolicyService<SecurityClientPinPolicyRecord, ClientPinPolicy, ClientPinPolicyDAO>
		implements IPolicyService<ClientPinPolicy> {

	private static final String CLIENT_PIN_POLICY = "client_pin_policy";

	private static final String CACHE_NAME_CLIENT_PIN_POLICY = "clientPinPolicy";

	@Override
	public String getPolicyName() {
		return CLIENT_PIN_POLICY;
	}

	@Override
	public String getPolicyCacheName() {
		return CACHE_NAME_CLIENT_PIN_POLICY;
	}

	@Override
	protected Mono<ClientPinPolicy> getDefaultPolicy() {
		return Mono.just(
				(ClientPinPolicy) new ClientPinPolicy()
						.setLength(UShort.valueOf(4))
						.setReLoginAfterInterval(ULong.valueOf(120))
						.setExpiryInDays(UShort.valueOf(30))
						.setExpiryWarnInDays(UShort.valueOf(25))
						.setPinHistoryCount(UShort.valueOf(3))
						.setClientId(ULong.valueOf(0))
						.setAppId(ULong.valueOf(0))
						.setNoFailedAttempts(UShort.valueOf(3))
						.setUserLockTimeMin(ULong.valueOf(30))
						.setId(DEFAULT_POLICY_ID)
		);
	}

	@PreAuthorize("hasAuthority('Authorities.Client_Password_Policy_READ')")
	@Override
	protected Mono<ClientPinPolicy> updatableEntity(ClientPinPolicy entity) {
		return this.read(entity.getId())
				.map(e -> {
					e.setNoFailedAttempts(entity.getNoFailedAttempts());
					e.setLength(entity.getLength());
					e.setReLoginAfterInterval(entity.getReLoginAfterInterval());
					e.setExpiryInDays(entity.getExpiryInDays());
					e.setExpiryWarnInDays(entity.getExpiryWarnInDays());
					e.setPinHistoryCount(entity.getPinHistoryCount());
					return e;
				});
	}

	@Override
	public AuthenticationPasswordType getAuthenticationPasswordType() {
		return AuthenticationPasswordType.PIN;
	}

	@Override
	public Mono<Boolean> checkAllConditions(ULong clientId, ULong appId, ULong userId, String password) {
		return null;
	}
}
