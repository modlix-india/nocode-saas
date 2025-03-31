package com.fincity.saas.notification.mq.action.service;

import com.fincity.saas.notification.document.Connection;
import com.fincity.saas.notification.model.request.SendRequest;

import reactor.core.publisher.Mono;

public interface IMessageService<T extends IMessageService<T>> {

	Mono<Boolean> execute(SendRequest request);

	Mono<Boolean> execute(Object message, Connection connection);
}
