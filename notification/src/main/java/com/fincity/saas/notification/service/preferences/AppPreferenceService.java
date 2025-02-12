package com.fincity.saas.notification.service.preferences;

import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.notification.dao.preference.AppPreferenceDao;
import com.fincity.saas.notification.dto.prefrence.AppPreference;
import com.fincity.saas.notification.jooq.tables.records.NotificationAppPreferenceRecord;
import com.fincity.saas.notification.service.NotificationMessageResourceService;

import reactor.core.publisher.Mono;

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

	@Override
	protected Mono<AppPreference> updatableEntity(AppPreference entity) {
		return null;
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {
		return null;
	}
}
