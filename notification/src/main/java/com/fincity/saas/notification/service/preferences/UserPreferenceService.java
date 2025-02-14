package com.fincity.saas.notification.service.preferences;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.notification.dao.preference.UserPreferenceDao;
import com.fincity.saas.notification.dto.prefrence.UserPreference;
import com.fincity.saas.notification.jooq.tables.records.NotificationUserPreferenceRecord;
import com.fincity.saas.notification.service.NotificationMessageResourceService;

@Service
public class UserPreferenceService
		extends NotificationPreferenceService<NotificationUserPreferenceRecord, UserPreference, UserPreferenceDao> {

	private static final String USER_PREFERENCE = "user_preference";

	private static final String CACHE_NAME_USER_PREFERENCE = "userPreference";

	protected UserPreferenceService(NotificationMessageResourceService messageResourceService, CacheService cacheService) {
		super(messageResourceService, cacheService);
	}

	@Override
	protected String getPreferenceName() {
		return USER_PREFERENCE;
	}

	@Override
	public String getPreferenceCacheName() {
		return CACHE_NAME_USER_PREFERENCE;
	}

	@Override
	public boolean isAppLevel() {
		return false;
	}
}
