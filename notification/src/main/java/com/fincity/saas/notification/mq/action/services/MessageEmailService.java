package com.fincity.saas.notification.mq.action.services;

import com.fincity.saas.notification.model.SendRequest;

import reactor.core.publisher.Mono;

public class MessageEmailService extends AbstractMessageService implements IMessageService {

	@Override
	public Mono<Boolean> execute(SendRequest request) {

		if (request == null || request.getChannels().getEmail() == null)
			return Mono.just(false);

		return null;
	}
}
