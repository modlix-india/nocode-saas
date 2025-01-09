package com.fincity.security.dao.policy;

import static com.fincity.security.jooq.tables.SecurityClientPinPolicy.SECURITY_CLIENT_PIN_POLICY;
import static com.fincity.security.jooq.tables.SecurityPastPins.SECURITY_PAST_PINS;

import java.util.List;

import org.jooq.Field;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.security.dto.PastPin;
import com.fincity.security.dto.policy.ClientPinPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientPinPolicyRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class ClientPinPolicyDAO extends AbstractPolicyDao<SecurityClientPinPolicyRecord, ClientPinPolicy> {

	public ClientPinPolicyDAO() {
		super(ClientPinPolicy.class, SECURITY_CLIENT_PIN_POLICY, SECURITY_CLIENT_PIN_POLICY.ID);
	}

	@Override
	protected Field<ULong> getClientIDField() {
		return SECURITY_CLIENT_PIN_POLICY.CLIENT_ID;
	}

	@Override
	protected Field<ULong> getAppIdField() {
		return SECURITY_CLIENT_PIN_POLICY.APP_ID;
	}

	public Flux<PastPin> getPastPinBasedOnPolicy(ClientPinPolicy clientPinPolicy, ULong userId) {
		return Flux.from(this.dslContext.select(SECURITY_PAST_PINS.fields())
				.from(SECURITY_PAST_PINS)
				.where(SECURITY_PAST_PINS.USER_ID.eq(userId))
				.orderBy(SECURITY_PAST_PINS.CREATED_AT.desc())
				.limit(clientPinPolicy.getPinHistoryCount()))
				.map(e -> e.into(PastPin.class));
	}
}
