package com.fincity.security.dao.policy;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;

import com.fincity.security.dao.clientcheck.AbstractUpdatableClientCheckDAO;
import com.fincity.security.dto.policy.AbstractPolicy;

import reactor.core.publisher.Mono;

public abstract class AbstractUpdatablePolicyDao<R extends UpdatableRecord<R>, D extends AbstractPolicy>
		extends AbstractUpdatableClientCheckDAO<R, ULong, D> {

	protected AbstractUpdatablePolicyDao(Class<D> pojoClass, Table<R> table, Field<ULong> idField) {
		super(pojoClass, table, idField);
	}

	protected abstract Field<ULong> getAppIdField();

	public Mono<D> getClientAppPolicy(ULong clientId, ULong appId) {
		return Mono.from(
				this.dslContext.selectFrom(this.table)
						.where(this.getClientIDField().eq(clientId).and(this.getAppIdField().eq(appId)))
		).map(e -> e.into(this.pojoClass));
	}
}
