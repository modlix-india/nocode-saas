package com.fincity.saas.notification.service.channel.inapp;

import java.util.HashMap;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.notification.dao.InAppNotificationDao;
import com.fincity.saas.notification.dto.InAppNotification;
import com.fincity.saas.notification.jooq.tables.records.NotificationInAppNotificationsRecord;
import com.fincity.saas.notification.service.AbstractCodeService;

import reactor.core.publisher.Mono;

@Service
public class InAppNotificationService
		extends AbstractCodeService<NotificationInAppNotificationsRecord, ULong, InAppNotification, InAppNotificationDao> {

	private static final String IN_APP_NOTIFICATION_CACHE = "inAppNotification";

	private CacheService cacheService;

	@Override
	protected String getCacheName() {
		return IN_APP_NOTIFICATION_CACHE;
	}

	@Override
	protected CacheService getCacheService() {
		return cacheService;
	}

	@Autowired
	private void setCacheService(CacheService cacheService) {
		this.cacheService = cacheService;
	}

	@Override
	protected Mono<InAppNotification> updatableEntity(InAppNotification entity) {
		return this.read(entity.getId())
				.map(e -> {
					e.setNotificationDeliveryStatus(entity.getNotificationDeliveryStatus());
					e.setSent(entity.isSent());
					e.setSentTime(entity.getSentTime());
					e.setDelivered(entity.isDelivered());
					e.setDeliveredTime(entity.getDeliveredTime());
					e.setRead(entity.isRead());
					e.setReadTime(entity.getReadTime());
					e.setFailed(entity.isFailed());
					e.setFailedTime(entity.getFailedTime());
					return e;
				});
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

		if (fields == null || key == null)
			return Mono.just(new HashMap<>());

		fields.remove("id");
		fields.remove(InAppNotification.Fields.code);
		fields.remove(InAppNotification.Fields.clientCode);
		fields.remove(InAppNotification.Fields.appCode);
		fields.remove(InAppNotification.Fields.userId);
		fields.remove(InAppNotification.Fields.inAppMessage);
		fields.remove(InAppNotification.Fields.notificationType);
		fields.remove("createdAt");
		fields.remove("createdBy");

		return Mono.just(fields);
	}
}
