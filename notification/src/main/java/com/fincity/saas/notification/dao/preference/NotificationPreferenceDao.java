package com.fincity.saas.notification.dao.preference;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.notification.dto.prefrence.NotificationPreference;
import com.fincity.saas.notification.enums.NotificationType;

import reactor.core.publisher.Mono;

public abstract class NotificationPreferenceDao<R extends UpdatableRecord<R>, D extends NotificationPreference<D>>
		extends AbstractUpdatableDAO<R, ULong, D> {

	private final Field<ULong> appIdField;
	private final Field<NotificationType> notificationTypeField;

	protected NotificationPreferenceDao(Class<D> pojoClass, Table<R> table, Field<ULong> idField, Field<ULong> appIdField,
	                                    Field<NotificationType> notificationTypeField) {
		super(pojoClass, table, idField);
		this.appIdField = appIdField;
		this.notificationTypeField = notificationTypeField;
	}

	protected abstract Field<ULong> getIdentifierField();

	public Mono<D> getNotificationPreference(ULong appId, ULong identifierId, NotificationType notificationTypeId) {
		return Mono.from(
				this.dslContext.selectFrom(this.table)
						.where(appIdField.eq(appId)
								.and(this.getIdentifierField().eq(identifierId))
								.and(notificationTypeField.eq(notificationTypeId)))
		).map(result -> result.into(this.pojoClass));
	}
}
