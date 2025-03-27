package com.fincity.saas.notification.service.channel.inapp;

import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import com.fincity.saas.notification.exception.NotificationDeliveryException;
import com.fincity.saas.notification.service.NotificationMessageResourceService;
import com.fincity.saas.notification.service.channel.AbstractChannelService;

import reactor.core.publisher.Mono;

public class AbstractInAppService extends AbstractChannelService {

	@Override
	protected <T> Mono<T> throwSendError(Object... params) {
		return msgService.throwMessage(msg -> new NotificationDeliveryException(this.getChannelType(), msg),
				NotificationMessageResourceService.MAIL_SEND_ERROR, params);
	}

	@Override
	public NotificationChannelType getChannelType() {
		return NotificationChannelType.IN_APP;
	}
}
