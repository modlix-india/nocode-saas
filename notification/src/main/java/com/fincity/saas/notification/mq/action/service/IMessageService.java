package com.fincity.saas.notification.mq.action.service;

import com.fincity.saas.notification.document.Connection;
import com.fincity.saas.notification.model.SendRequest;

import reactor.core.publisher.Mono;

public interface IMessageService {

	Mono<Boolean> execute(SendRequest request);

	Mono<Boolean> execute(Object message, Connection connection);
}
