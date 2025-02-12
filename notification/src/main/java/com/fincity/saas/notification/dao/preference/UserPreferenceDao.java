package com.fincity.saas.notification.dao.preference;

import static com.fincity.saas.notification.jooq.tables.NotificationUserPreference.NOTIFICATION_USER_PREFERENCE;

import com.fincity.saas.notification.dto.prefrence.UserPreference;
import com.fincity.saas.notification.jooq.tables.records.NotificationUserPreferenceRecord;

public class UserPreferenceDao extends NotificationPreferenceDao<NotificationUserPreferenceRecord, UserPreference> {

	protected UserPreferenceDao() {
		super(UserPreference.class, NOTIFICATION_USER_PREFERENCE, NOTIFICATION_USER_PREFERENCE.ID);
	}
}
