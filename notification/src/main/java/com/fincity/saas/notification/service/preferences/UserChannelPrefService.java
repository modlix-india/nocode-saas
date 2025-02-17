package com.fincity.saas.notification.service.preferences;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.notification.dao.preferences.UserChannelPrefDao;
import com.fincity.saas.notification.dto.preference.UserChannelPref;
import com.fincity.saas.notification.enums.NotificationChannelType;
import com.fincity.saas.notification.jooq.tables.records.NotificationUserChannelPrefRecord;
import com.fincity.saas.notification.service.NotificationMessageResourceService;

import reactor.core.publisher.Mono;

@Service
public class UserChannelPrefService extends UserPrefService<NotificationUserChannelPrefRecord, NotificationChannelType,
		UserChannelPref, UserChannelPrefDao> {

	private static final String CHANNEL_PREF = "UserChannelPref";

	private static final String CHANNEL_PREF_CACHE = "UserChannelPrefCache";

	protected UserChannelPrefService(NotificationMessageResourceService messageResourceService, CacheService cacheService) {
		super(messageResourceService, cacheService);
	}

	@Override
	protected String getPreferenceName() {
		return CHANNEL_PREF;
	}

	@Override
	protected String getPreferenceCacheName() {
		return CHANNEL_PREF_CACHE;
	}

	@Override
	protected boolean containsOnlyDisable() {
		return Boolean.FALSE;
	}

	@Override
	public Mono<UserChannelPref> updateCreationEntity(UserChannelPref entity) {

		if (this.containsOnlyDisable())
			entity.setEnabled(Boolean.FALSE);

		if (entity.getChannelType().equals(NotificationChannelType.DISABLED))
			entity.setEnabled(Boolean.FALSE);

		return Mono.just(entity);
	}
}
