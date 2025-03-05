package com.fincity.saas.notification.mq.action.services;

import org.springframework.stereotype.Service;

import com.fincity.saas.notification.enums.NotificationChannelType;
import com.fincity.saas.notification.model.SendRequest;

import reactor.core.publisher.Mono;

@Service
public class MessageEmailService extends AbstractMessageService implements IMessageService {

	@Override
	public NotificationChannelType getChannelType() {
		return NotificationChannelType.EMAIL;
	}

	@Override
	public Mono<Boolean> execute(SendRequest request) {

		if (!super.isValid(request))
			return Mono.just(false);

		return null;
	}
}
