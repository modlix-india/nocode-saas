package com.fincity.saas.notification.dao.preferences;

import static com.fincity.saas.notification.jooq.tables.NotificationUserChannelPref.NOTIFICATION_USER_CHANNEL_PREF;

import org.jooq.Field;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.notification.dto.preference.UserChannelPref;
import com.fincity.saas.notification.enums.NotificationChannelType;
import com.fincity.saas.notification.jooq.tables.records.NotificationUserChannelPrefRecord;

@Component
public class UserChannelPrefDao extends UserPrefDao<NotificationUserChannelPrefRecord, NotificationChannelType, UserChannelPref> {

	protected UserChannelPrefDao() {
		super(UserChannelPref.class, NOTIFICATION_USER_CHANNEL_PREF,
				NOTIFICATION_USER_CHANNEL_PREF.ID, NOTIFICATION_USER_CHANNEL_PREF.CODE);
	}

	@Override
	protected Field<ULong> getAppIdField() {
		return NOTIFICATION_USER_CHANNEL_PREF.APP_ID;
	}

	@Override
	protected Field<ULong> getUserIdField() {
		return NOTIFICATION_USER_CHANNEL_PREF.USER_ID;
	}

	@Override
	protected Field<NotificationChannelType> getTypeField() {
		return NOTIFICATION_USER_CHANNEL_PREF.CHANNEL_TYPE;
	}
}
