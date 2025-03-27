package com.fincity.saas.notification.service.channel.inapp;

import com.fincity.saas.notification.document.Connection;
import com.fincity.saas.notification.model.SendRequest;

import reactor.core.publisher.Mono;

public interface IInAppService {

	Mono<Boolean> sendMessage(SendRequest inAppMessage, Connection connection);

}
