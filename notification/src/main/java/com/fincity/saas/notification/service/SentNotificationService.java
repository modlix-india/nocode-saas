package com.fincity.saas.notification.service;

import java.time.LocalDateTime;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.notification.dao.SentNotificationDao;
import com.fincity.saas.notification.dto.SentNotification;
import com.fincity.saas.notification.enums.NotificationDeliveryStatus;
import com.fincity.saas.notification.enums.NotificationStage;
import com.fincity.saas.notification.jooq.tables.records.NotificationSentNotificationsRecord;
import com.fincity.saas.notification.model.SendRequest;

import reactor.core.publisher.Mono;

@Service
public class SentNotificationService
		extends AbstractCodeService<NotificationSentNotificationsRecord, ULong, SentNotification, SentNotificationDao> {

	@Override
	protected Mono<SentNotification> updatableEntity(SentNotification entity) {
		return null;
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {
		return null;
	}

	public Mono<SentNotification> toPlatformNotification(SendRequest request) {
		return this.createSentNotification(request, NotificationStage.PLATFORM, NotificationDeliveryStatus.CREATED);
	}

	public Mono<SentNotification> toPlatformNotification(SendRequest request,
			NotificationDeliveryStatus deliveryStatus) {
		return this.createSentNotification(request, NotificationStage.PLATFORM, deliveryStatus);
	}

	public Mono<SentNotification> toGatewayNotification(SendRequest request,
			NotificationDeliveryStatus deliveryStatus) {
		return this.updateSentNotification(request, NotificationStage.GATEWAY, deliveryStatus);
	}

	public Mono<SentNotification> toNetworkNotification(SendRequest request,
			NotificationDeliveryStatus deliveryStatus) {
		return this.updateSentNotification(request, NotificationStage.NETWORK, deliveryStatus);
	}

	private Mono<SentNotification> createSentNotification(SendRequest request, NotificationStage notificationStage,
			NotificationDeliveryStatus deliveryStatus) {
		return this.createSentNotification(request, LocalDateTime.now(), notificationStage, deliveryStatus)
				.flatMap(this::create);
	}

	private Mono<SentNotification> updateSentNotification(SendRequest request, NotificationStage notificationStage,
			NotificationDeliveryStatus deliveryStatus) {
		return FlatMapUtil.flatMapMono(
				() -> this.getByCode(request.getCode())
						.switchIfEmpty(this.createSentNotification(request, notificationStage, deliveryStatus)),
				sentNotification -> this.updateSentNotification(sentNotification, request, LocalDateTime.now(),
						notificationStage, deliveryStatus),
				(sentNotification, updated) -> this.update(updated));
	}

	private Mono<SentNotification> createSentNotification(SendRequest request, LocalDateTime createdTime,
			NotificationStage notificationStage, NotificationDeliveryStatus deliveryStatus) {

		SentNotification sentNotification = SentNotification.from(request, createdTime)
				.setNotificationStage(notificationStage);

		if (request.getChannels().containsAnyChannel()) {
			Map<String, LocalDateTime> createdInfo = Map.of(deliveryStatus.getLiteral(), createdTime);
			request.getChannels().getEnabledChannels().forEach(channelType -> sentNotification
					.setChannelInfo(channelType, Boolean.TRUE, createdInfo, Boolean.TRUE));
		}

		return Mono.just(sentNotification);
	}

	private Mono<SentNotification> updateSentNotification(SentNotification sentNotification, SendRequest request,
			LocalDateTime createdTime, NotificationStage notificationStage, NotificationDeliveryStatus deliveryStatus) {

		sentNotification.setNotificationStage(notificationStage);

		if (request.getChannels().containsAnyChannel()) {
			Map<String, LocalDateTime> createdInfo = Map.of(deliveryStatus.getLiteral(), createdTime);
			request.getChannels().getEnabledChannels().forEach(channelType -> sentNotification
					.setChannelInfo(channelType, Boolean.TRUE, createdInfo, Boolean.FALSE));
		}

		return Mono.just(sentNotification);

	}
}
