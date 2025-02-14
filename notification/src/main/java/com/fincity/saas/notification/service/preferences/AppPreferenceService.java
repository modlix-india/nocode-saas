package com.fincity.saas.notification.service.preferences;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.notification.dao.preference.AppPreferenceDao;
import com.fincity.saas.notification.dto.prefrence.AppPreference;
import com.fincity.saas.notification.jooq.tables.records.NotificationAppPreferenceRecord;
import com.fincity.saas.notification.service.NotificationMessageResourceService;

@Service
public class AppPreferenceService
		extends NotificationPreferenceService<NotificationAppPreferenceRecord, AppPreference, AppPreferenceDao> {

	private static final String APP_PREFERENCE = "app_preference";

	private static final String CACHE_NAME_APP_PREFERENCE = "appPreference";

	protected AppPreferenceService(NotificationMessageResourceService messageResourceService, CacheService cacheService) {
		super(messageResourceService, cacheService);
	}

	@Override
	protected String getPreferenceName() {
		return APP_PREFERENCE;
	}

	@Override
	public String getPreferenceCacheName() {
		return CACHE_NAME_APP_PREFERENCE;
	}

	@Override
	public boolean isAppLevel() {
		return true;
	}
}
