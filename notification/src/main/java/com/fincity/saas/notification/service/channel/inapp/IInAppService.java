package com.fincity.saas.notification.service.channel.inapp;

import com.fincity.saas.notification.document.common.core.Connection;
import com.fincity.saas.notification.model.request.SendRequest;
import reactor.core.publisher.Mono;

public interface IInAppService<T extends IInAppService<T>> {

    Mono<Boolean> sendMessage(SendRequest inAppMessage, Connection connection);
}
