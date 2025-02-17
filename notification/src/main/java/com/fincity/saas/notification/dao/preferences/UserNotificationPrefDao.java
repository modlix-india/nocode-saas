package com.fincity.saas.notification.dao.preferences;

import static com.fincity.saas.notification.jooq.tables.NotificationUserNotificationPref.NOTIFICATION_USER_NOTIFICATION_PREF;

import org.jooq.Field;
import org.jooq.types.ULong;

import com.fincity.saas.notification.dto.preference.UserNotificationPref;
import com.fincity.saas.notification.jooq.tables.records.NotificationUserNotificationPrefRecord;

public class UserNotificationPrefDao extends UserPrefDao<NotificationUserNotificationPrefRecord, String, UserNotificationPref> {

	protected UserNotificationPrefDao() {
		super(UserNotificationPref.class, NOTIFICATION_USER_NOTIFICATION_PREF,
				NOTIFICATION_USER_NOTIFICATION_PREF.ID, NOTIFICATION_USER_NOTIFICATION_PREF.CODE);
	}

	@Override
	protected Field<ULong> getAppIdField() {
		return NOTIFICATION_USER_NOTIFICATION_PREF.APP_ID;
	}

	@Override
	protected Field<ULong> getUserIdField() {
		return NOTIFICATION_USER_NOTIFICATION_PREF.USER_ID;
	}

	@Override
	protected Field<String> getTypeField() {
		return NOTIFICATION_USER_NOTIFICATION_PREF.NOTIFICATION_NAME;
	}
}
