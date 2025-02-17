package com.fincity.saas.notification.dao.preferences;

import java.io.Serializable;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;

import com.fincity.saas.notification.dao.AbstractCodeDao;
import com.fincity.saas.notification.dto.preference.UserPref;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class UserPrefDao<R extends UpdatableRecord<R>, T extends Serializable, D extends UserPref<T, D>> extends AbstractCodeDao<R, ULong, D> {

	protected UserPrefDao(Class<D> pojoClass, Table<R> table, Field<ULong> idField, Field<String> codeField) {
		super(pojoClass, table, idField, codeField);
	}

	protected abstract Field<ULong> getAppIdField();

	protected abstract Field<ULong> getUserIdField();

	protected abstract Field<T> getTypeField();

	public Mono<D> getUserPreferences(ULong appId, ULong userId, T type) {
		return Mono.from(
				this.dslContext.selectFrom(this.table)
						.where(this.getAppIdField().eq(appId))
						.and(this.getUserIdField().eq(userId))
						.and(this.getTypeField().eq(type))
		).map(result -> result.into(this.pojoClass));
	}

	public Flux<D> getUserPreferences(ULong appId, ULong userId) {
		return Flux.from(
				this.dslContext.selectFrom(this.table)
						.where(this.getAppIdField().eq(appId))
						.and(this.getUserIdField().eq(userId))
		).map(result -> result.into(this.pojoClass));
	}

}
