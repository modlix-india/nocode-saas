package com.fincity.saas.notification.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.notification.dao.SentNotificationDao;
import com.fincity.saas.notification.dto.SentNotification;
import com.fincity.saas.notification.enums.NotificationChannelType;
import com.fincity.saas.notification.enums.NotificationDeliveryStatus;
import com.fincity.saas.notification.enums.NotificationStage;
import com.fincity.saas.notification.jooq.tables.records.NotificationSentNotificationsRecord;
import com.fincity.saas.notification.model.SendRequest;

import reactor.core.publisher.Mono;

@Service
public class SentNotificationService
		extends AbstractCodeService<NotificationSentNotificationsRecord, ULong, SentNotification, SentNotificationDao> {

	private static final String SENT_NOTIFICATION_CACHE = "sentNotification";

	private CacheService cacheService;

	@Override
	protected String getCacheName() {
		return SENT_NOTIFICATION_CACHE;
	}

	@Override
	protected CacheService getCacheService() {
		return cacheService;
	}

	@Autowired
	public void setCacheService(CacheService cacheService) {
		this.cacheService = cacheService;
	}

	@Override
	protected Mono<SentNotification> updatableEntity(SentNotification entity) {
		return this.read(entity.getId())
				.map(e -> {
					e.setNotificationStage(entity.getNotificationStage());
					e.setEmail(entity.isEmail());
					e.setEmailDeliveryStatus(entity.getEmailDeliveryStatus());
					e.setInApp(entity.isInApp());
					e.setInAppDeliveryStatus(entity.getInAppDeliveryStatus());
					e.setMobilePush(entity.isMobilePush());
					e.setMobilePushDeliveryStatus(entity.getMobilePushDeliveryStatus());
					e.setWebPush(entity.isWebPush());
					e.setWebPushDeliveryStatus(entity.getWebPushDeliveryStatus());
					e.setSms(entity.isSms());
					e.setSmsDeliveryStatus(entity.getSmsDeliveryStatus());
					e.setErrorCode(entity.getErrorCode());
					e.setErrorMessageId(entity.getErrorMessageId());
					e.setErrorMessage(entity.getErrorMessage());
					return e;
				});
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {

		if (fields == null || key == null)
			return Mono.just(new HashMap<>());

		fields.remove("id");
		fields.remove(SentNotification.Fields.code);
		fields.remove(SentNotification.Fields.clientCode);
		fields.remove(SentNotification.Fields.appCode);
		fields.remove(SentNotification.Fields.userId);
		fields.remove(SentNotification.Fields.notificationType);
		fields.remove(SentNotification.Fields.notificationMessage);
		fields.remove(SentNotification.Fields.triggerTime);
		fields.remove("createdAt");
		fields.remove("createdBy");

		return Mono.just(fields);
	}

	public Mono<SentNotification> toPlatformNotification(SendRequest request) {
		return this.createFromRequest(request, NotificationStage.PLATFORM, NotificationDeliveryStatus.CREATED);
	}

	public Mono<SentNotification> toPlatformNotification(SendRequest request, NotificationDeliveryStatus deliveryStatus,
			NotificationChannelType... channelTypes) {
		return this.createFromRequest(request, NotificationStage.PLATFORM, deliveryStatus, channelTypes);
	}

	public Mono<SentNotification> toGatewayNotification(SendRequest request, NotificationDeliveryStatus deliveryStatus,
			NotificationChannelType... channelTypes) {
		return this.updateFromRequest(request, NotificationStage.GATEWAY, deliveryStatus, channelTypes);
	}

	public Mono<SentNotification> toNetworkNotification(SendRequest request, NotificationDeliveryStatus deliveryStatus,
			NotificationChannelType... channelTypes) {
		return this.updateFromRequest(request, NotificationStage.NETWORK, deliveryStatus, channelTypes);
	}

	private Mono<SentNotification> createFromRequest(SendRequest request, NotificationStage notificationStage,
			NotificationDeliveryStatus deliveryStatus, NotificationChannelType... channelTypes) {
		return this
				.createSentNotification(request, LocalDateTime.now(), notificationStage, deliveryStatus, channelTypes)
				.flatMap(this::create);
	}

	private Mono<SentNotification> updateFromRequest(SendRequest request, NotificationStage notificationStage,
			NotificationDeliveryStatus deliveryStatus, NotificationChannelType... channelTypes) {

		return FlatMapUtil.flatMapMono(

				() -> this.getByCode(request.getCode())
						.switchIfEmpty(
								this.createFromRequest(request, notificationStage, deliveryStatus, channelTypes)),

				sentNotification -> this.updateSentNotification(sentNotification, request, LocalDateTime.now(),
						notificationStage, deliveryStatus, channelTypes),

				(sentNotification, updatedNotification) -> this.update(updatedNotification),

				(sentNotification, updatedNotification, updated) -> this.evictCode(updated.getCode()).map(evictedCode-> updated)
		);
	}

	private Mono<SentNotification> createSentNotification(SendRequest request, LocalDateTime createdTime,
			NotificationStage notificationStage, NotificationDeliveryStatus deliveryStatus,
			NotificationChannelType... channelTypes) {

		SentNotification sentNotification = SentNotification.from(request, createdTime);

		return this.updateStageAndStatus(sentNotification, request, createdTime, notificationStage, deliveryStatus,
				channelTypes);
	}

	private Mono<SentNotification> updateSentNotification(SentNotification sentNotification, SendRequest request,
			LocalDateTime createdTime, NotificationStage notificationStage, NotificationDeliveryStatus deliveryStatus,
			NotificationChannelType... channelTypes) {
		return this.updateStageAndStatus(sentNotification, request, createdTime, notificationStage, deliveryStatus,
				channelTypes);
	}

	private Mono<SentNotification> updateStageAndStatus(SentNotification sentNotification, SendRequest request,
			LocalDateTime createdTime, NotificationStage notificationStage, NotificationDeliveryStatus deliveryStatus,
			NotificationChannelType... channelTypes) {

		sentNotification.setNotificationStage(notificationStage);

		if (!request.getChannels().containsAnyChannel())
			return Mono.just(sentNotification);

		Map<String, LocalDateTime> deliveryStatusInfo = Map.of(deliveryStatus.getLiteral(), createdTime);

		List<NotificationChannelType> channels = channelTypes.length > 0
				? Arrays.stream(channelTypes).filter(ct -> request.getChannels().getEnabledChannels().contains(ct))
						.toList()
				: new ArrayList<>(request.getChannels().getEnabledChannels());

		channels.forEach(ct -> sentNotification
				.setChannelInfo(ct, Boolean.TRUE, deliveryStatusInfo, Boolean.TRUE));

		return Mono.just(sentNotification);
	}
}
