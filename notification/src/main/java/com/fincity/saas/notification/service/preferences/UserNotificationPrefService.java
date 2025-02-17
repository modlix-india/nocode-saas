package com.fincity.saas.notification.service.preferences;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.notification.dao.preferences.UserNotificationPrefDao;
import com.fincity.saas.notification.dto.preference.UserNotificationPref;
import com.fincity.saas.notification.jooq.tables.records.NotificationUserNotificationPrefRecord;
import com.fincity.saas.notification.service.NotificationMessageResourceService;

import reactor.core.publisher.Mono;

@Service
public class UserNotificationPrefService extends UserPrefService<NotificationUserNotificationPrefRecord, String,
		UserNotificationPref, UserNotificationPrefDao> {

	private static final String USER_NOTIFICATION_PREF = "UserNotificationPref";

	private static final String USER_NOTIFICATION_PREF_CACHE = "UserNotificationPrefCache";

	protected UserNotificationPrefService(NotificationMessageResourceService messageResourceService, CacheService cacheService) {
		super(messageResourceService, cacheService);
	}

	@Override
	protected String getPreferenceName() {
		return USER_NOTIFICATION_PREF;
	}

	@Override
	protected String getPreferenceCacheName() {
		return USER_NOTIFICATION_PREF_CACHE;
	}

	@Override
	protected boolean containsOnlyDisable() {
		return Boolean.TRUE;
	}

	@Override
	public Mono<UserNotificationPref> updateCreationEntity(UserNotificationPref entity) {

		if (this.containsOnlyDisable())
			entity.setEnabled(Boolean.FALSE);

		return Mono.just(entity);
	}
}
