package com.fincity.saas.notification.service.channel;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.notification.enums.NotificationDeliveryStatus;
import com.fincity.saas.notification.enums.NotificationStage;
import com.fincity.saas.notification.enums.channel.ChannelType;
import com.fincity.saas.notification.model.request.SendRequest;

import reactor.core.publisher.Mono;

public interface IChannelStorageService<I extends Serializable, D extends AbstractUpdatableDTO<I, I>, T extends IChannelStorageService<I, D, T>>
		extends ChannelType {

	Mono<D> saveChannelNotification(SendRequest request, LocalDateTime triggerTime,
			NotificationStage notificationStage, NotificationDeliveryStatus deliveryStatus);

	Mono<D> updateChannelNotification(SendRequest request, LocalDateTime createdTime,
			NotificationStage notificationStage, NotificationDeliveryStatus deliveryStatus);
}
