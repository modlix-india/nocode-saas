package com.fincity.saas.notification.mq.action.services;

import com.fincity.saas.notification.model.SendRequest;

import reactor.core.publisher.Mono;

public interface IMessageService {

	Mono<Boolean> execute(SendRequest request);

}
