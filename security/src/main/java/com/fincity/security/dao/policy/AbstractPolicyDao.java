package com.fincity.security.dao.policy;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;

import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.security.dao.AbstractClientCheckDAO;
import com.fincity.security.dto.policy.AbstractPolicy;
import static com.fincity.security.jooq.tables.SecurityApp.SECURITY_APP;
import static com.fincity.security.jooq.tables.SecurityClient.SECURITY_CLIENT;
import static com.fincity.security.jooq.tables.SecurityClientOtpPolicy.SECURITY_CLIENT_OTP_POLICY;

import reactor.core.publisher.Mono;

public abstract class AbstractPolicyDao<R extends UpdatableRecord<R>, D extends AbstractPolicy>
		extends AbstractClientCheckDAO<R, ULong, D> {

	protected AbstractPolicyDao(Class<D> pojoClass, Table<R> table, Field<ULong> idField) {
		super(pojoClass, table, idField);
	}

	protected abstract Field<ULong> getAppIdField();

	protected abstract Table<R> getTable();

	public Mono<D> getClientAppPolicy(ULong clientId, ULong appId, ULong loggedInClientId) {

		return this.readAll(
				ComplexCondition.and(
						ComplexCondition.or(
								FilterCondition.make(this.getClientIDField().getName(), clientId),
								FilterCondition.make(this.getClientIDField().getName(), loggedInClientId)),
						FilterCondition.make(this.getAppIdField().getName(), appId)))
				.collectList()
				.flatMap(e -> {
					if (e.isEmpty())
						return Mono.empty();

					if (e.size() == 1)
						return Mono.just(e.getFirst());

					return Mono.just(e.get(clientId.equals(e.getFirst().getClientId()) ? 0 : 1));
				});
	}

	public Mono<D> getClientAppPolicy(String clientCode, String appCode) {
		return Mono.from(this.dslContext.select(getTable().fields())
				.from(SECURITY_CLIENT_OTP_POLICY)
				.join(SECURITY_CLIENT).on(getClientIDField().eq(SECURITY_CLIENT.ID))
				.join(SECURITY_APP).on(getAppIdField().eq(SECURITY_APP.ID))
				.where(SECURITY_CLIENT.CODE.eq(clientCode)
						.and(SECURITY_APP.APP_CODE.eq(appCode))))
				.map(e -> this.pojoClass.cast(e.into(this.pojoClass)));

	}
}
