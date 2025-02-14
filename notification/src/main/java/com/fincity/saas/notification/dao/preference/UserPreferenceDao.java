package com.fincity.saas.notification.dao.preference;

import static com.fincity.saas.notification.jooq.tables.NotificationUserPreference.NOTIFICATION_USER_PREFERENCE;

import org.jooq.Field;
import org.jooq.types.ULong;

import com.fincity.saas.notification.dto.prefrence.UserPreference;
import com.fincity.saas.notification.jooq.tables.records.NotificationUserPreferenceRecord;

public class UserPreferenceDao extends NotificationPreferenceDao<NotificationUserPreferenceRecord, UserPreference> {

	protected UserPreferenceDao() {
		super(UserPreference.class, NOTIFICATION_USER_PREFERENCE, NOTIFICATION_USER_PREFERENCE.ID,
				NOTIFICATION_USER_PREFERENCE.APP_ID, NOTIFICATION_USER_PREFERENCE.CODE);
	}

	@Override
	protected Field<ULong> getIdentifierField() {
		return NOTIFICATION_USER_PREFERENCE.USER_ID;
	}
}
