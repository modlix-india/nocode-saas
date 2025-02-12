package com.fincity.saas.notification.dao.preference;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.notification.dto.prefrence.NotificationPreference;

public abstract class NotificationPreferenceDao<R extends UpdatableRecord<R>, D extends NotificationPreference<D>>
		extends AbstractUpdatableDAO<R, ULong, D> {

	protected NotificationPreferenceDao(Class<D> pojoClass, Table<R> table, Field<ULong> idField) {
		super(pojoClass, table, idField);
	}
}
