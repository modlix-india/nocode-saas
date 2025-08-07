package com.fincity.security.service.policy;

import org.jooq.types.ULong;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.policy.ClientPinPolicyDAO;
import com.fincity.security.dto.PastPin;
import com.fincity.security.dto.policy.ClientPinPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientPinPolicyRecord;
import com.fincity.security.model.AuthenticationPasswordType;
import com.fincity.security.service.SecurityMessageResourceService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ClientPinPolicyService
		extends AbstractPolicyService<SecurityClientPinPolicyRecord, ClientPinPolicy, ClientPinPolicyDAO>
		implements IPolicyService<ClientPinPolicy> {

	private static final String CLIENT_PIN_POLICY = "client_pin_policy";

	private static final String CACHE_NAME_CLIENT_PIN_POLICY = "clientPinPolicy";

	private final PasswordEncoder encoder;

	protected ClientPinPolicyService(SecurityMessageResourceService securityMessageResourceService,
			CacheService cacheService, PasswordEncoder encoder) {
		super(securityMessageResourceService, cacheService);
		this.encoder = encoder;
	}

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
						.setLength((short) 4)
						.setReLoginAfterInterval(120L)
						.setExpiryInDays((short) 30)
						.setExpiryWarnInDays((short) 25)
						.setPinHistoryCount((short) 3)
						.setClientId(ULong.valueOf(0))
						.setAppId(ULong.valueOf(0))
						.setNoFailedAttempts((short) 3)
						.setUserLockTime(15L)
						.setId(DEFAULT_POLICY_ID));
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
					e.setNoFailedAttempts(
							entity.getNoFailedAttempts() != null ? entity.getNoFailedAttempts() : (short) 3);
					e.setUserLockTime(entity.getUserLockTime() != null ? entity.getUserLockTime() : 15L);
					return e;
				});
	}

	@Override
	public AuthenticationPasswordType getAuthenticationPasswordType() {
		return AuthenticationPasswordType.PIN;
	}

	@Override
	public Mono<Boolean> checkAllConditions(ULong clientId, ULong appId, ULong userId, String password) {

		return FlatMapUtil.flatMapMono(

				() -> this.getClientAppPolicy(clientId, appId),

				pinPolicy -> this.checkAllConditions(pinPolicy, userId, password))
				.contextWrite(
						Context.of(LogUtil.METHOD_NAME, "ClientPinPolicyService.checkAllConditions[clientId, AppId]"))
				.defaultIfEmpty(Boolean.TRUE);
	}

	@Override
	public Mono<Boolean> checkAllConditions(ClientPinPolicy policy, ULong userId, String password) {
		return FlatMapUtil.flatMapMono(

				() -> this.checkLength(policy, password),

				lengthCheck -> this.checkPastPins(policy, userId, password))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientPinPolicyService.checkAllConditions[policy]"))
				.defaultIfEmpty(Boolean.TRUE);
	}

	private Mono<Boolean> checkPastPins(ClientPinPolicy pinPolicy, ULong userId, String pin) {

		if (userId == null)
			return Mono.just(Boolean.TRUE);

		return this.dao.getPastPinBasedOnPolicy(pinPolicy, userId)
				.filter(pastPin -> isPinMatch(pastPin, userId, pin))
				.next()
				.flatMap(matchedPin -> policyBadRequestException(
						SecurityMessageResourceService.PASSWORD_USER_ERROR,
						getAuthenticationPasswordType().getName(),
						getAuthenticationPasswordType().getName()))
				.switchIfEmpty(Mono.just(Boolean.TRUE));
	}

	private boolean isPinMatch(PastPin pastPin, ULong userId, String pin) {
		return pastPin.isPinHashed() ? encoder.matches(userId + pin, pastPin.getPin()) : pastPin.getPin().equals(pin);
	}

	private Mono<Boolean> checkLength(ClientPinPolicy pinPolicy, String pin) {

		if (pinPolicy.getLength() != null && pin.length() != pinPolicy.getLength().intValue())
			return this.policyBadRequestException(SecurityMessageResourceService.LENGTH_ERROR,
					this.getAuthenticationPasswordType().getName(), pinPolicy.getLength().intValue());

		return Mono.just(Boolean.TRUE);
	}
}
