package com.fincity.saas.notification.dao.preference;

import static com.fincity.saas.notification.jooq.tables.NotificationAppPreference.NOTIFICATION_APP_PREFERENCE;

import org.springframework.stereotype.Component;

import com.fincity.saas.notification.dto.prefrence.AppPreference;
import com.fincity.saas.notification.jooq.tables.records.NotificationAppPreferenceRecord;

@Component
public class AppPreferenceDao extends NotificationPreferenceDao<NotificationAppPreferenceRecord, AppPreference> {

	protected AppPreferenceDao() {
		super(AppPreference.class, NOTIFICATION_APP_PREFERENCE, NOTIFICATION_APP_PREFERENCE.ID);
	}
}
