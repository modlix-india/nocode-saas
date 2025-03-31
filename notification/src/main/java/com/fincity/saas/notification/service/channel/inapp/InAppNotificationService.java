package com.fincity.saas.notification.service.channel.inapp;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.notification.dao.InAppNotificationDao;
import com.fincity.saas.notification.dto.InAppNotification;
import com.fincity.saas.notification.enums.NotificationDeliveryStatus;
import com.fincity.saas.notification.enums.NotificationStage;
import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import com.fincity.saas.notification.jooq.tables.records.NotificationInAppNotificationsRecord;
import com.fincity.saas.notification.model.request.SendRequest;
import com.fincity.saas.notification.service.AbstractCodeService;
import com.fincity.saas.notification.service.channel.IChannelStorageService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service(value = "inAppStorageService")
public class InAppNotificationService extends
		AbstractCodeService<NotificationInAppNotificationsRecord, ULong, InAppNotification, InAppNotificationDao>
		implements IChannelStorageService<ULong, InAppNotification, InAppNotificationService> {

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
	protected Mono<ULong> getLoggedInUserId() {
		return FlatMapUtil.flatMapMono(
				SecurityContextUtil::getUsersContextAuthentication,
				ca -> Mono.justOrEmpty(ca.isAuthenticated() ? ULong.valueOf(ca.getUser().getId()) : null));
	}

	@Override
	public NotificationChannelType getChannelType() {
		return NotificationChannelType.IN_APP;
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

	@Override
	public Mono<InAppNotification> saveChannelNotification(SendRequest request, LocalDateTime triggerTime,
			NotificationStage notificationStage, NotificationDeliveryStatus deliveryStatus) {
		return this
				.updateStageAndStatus(InAppNotification.from(request, triggerTime), triggerTime, notificationStage,
						deliveryStatus)
				.flatMap(this::create)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "InAppNotificationService.saveChannelNotification"));
	}

	@Override
	public Mono<InAppNotification> updateChannelNotification(SendRequest request, LocalDateTime createdTime,
			NotificationStage notificationStage, NotificationDeliveryStatus deliveryStatus) {

		return FlatMapUtil.flatMapMono(
				() -> super.getByCode(request.getCode()),

				inAppNotification -> this.updateStageAndStatus(inAppNotification, createdTime, notificationStage,
						deliveryStatus),

				(inAppNotification, uInAppNotification) -> this.update(uInAppNotification),

				(inAppNotification, uInAppNotification, updated) -> this.evictCode(updated.getCode())
						.map(evicted -> updated))
				.switchIfEmpty(this.saveChannelNotification(request, createdTime, notificationStage, deliveryStatus))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "InAppNotificationService.updateChannelNotification"));
	}

	private Mono<InAppNotification> updateStageAndStatus(InAppNotification inAppNotification, LocalDateTime createdTime,
			NotificationStage notificationStage, NotificationDeliveryStatus deliveryStatus) {

		if (inAppNotification == null)
			return Mono.empty();

		if (notificationStage != null)
			inAppNotification.setNotificationStage(notificationStage);

		return Mono.just(inAppNotification.setNotificationDeliveryStatus(deliveryStatus, createdTime));
	}
}
