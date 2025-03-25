package com.fincity.saas.notification.mq.action.service;

import com.fincity.saas.notification.document.Connection;
import com.fincity.saas.notification.model.SendRequest;
import com.fincity.saas.notification.model.message.NotificationMessage;

import reactor.core.publisher.Mono;

public interface IMessageService {

	Mono<Boolean> execute(SendRequest request);

	<T extends NotificationMessage<T>> Mono<Boolean> execute(T message, Connection connection);
}
