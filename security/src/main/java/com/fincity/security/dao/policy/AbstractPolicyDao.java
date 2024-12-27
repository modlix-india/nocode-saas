package com.fincity.security.dao.policy;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;

import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.security.dao.AbstractClientCheckDAO;
import com.fincity.security.dto.policy.AbstractPolicy;

import reactor.core.publisher.Mono;

public abstract class AbstractPolicyDao<R extends UpdatableRecord<R>, D extends AbstractPolicy>
		extends AbstractClientCheckDAO<R, ULong, D> {

	protected AbstractPolicyDao(Class<D> pojoClass, Table<R> table, Field<ULong> idField) {
		super(pojoClass, table, idField);
	}

	protected abstract Field<ULong> getAppIdField();

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
}
