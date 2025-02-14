package com.fincity.saas.notification.dao.preference;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;

import com.fincity.saas.notification.dao.AbstractCodeDao;
import com.fincity.saas.notification.dto.prefrence.NotificationPreference;

import reactor.core.publisher.Mono;

public abstract class NotificationPreferenceDao<R extends UpdatableRecord<R>, D extends NotificationPreference<D>>
		extends AbstractCodeDao<R, ULong, D> {

	private final Field<ULong> appIdField;

	protected NotificationPreferenceDao(Class<D> pojoClass, Table<R> table, Field<ULong> idField, Field<ULong> appIdField, Field<String> codeField) {
		super(pojoClass, table, idField, codeField);
		this.appIdField = appIdField;
	}

	protected abstract Field<ULong> getIdentifierField();

	public Mono<D> getNotificationPreference(ULong appId, ULong identifierId) {
		return Mono.from(
				this.dslContext.selectFrom(this.table)
						.where(appIdField.eq(appId)
								.and(this.getIdentifierField().eq(identifierId)))
		).map(result -> result.into(this.pojoClass));
	}
}
