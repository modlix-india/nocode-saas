package com.fincity.saas.notification.service.channel.email;

import com.fincity.saas.notification.model.message.channel.EmailMessage;
import com.fincity.saas.notification.oserver.core.document.Connection;
import reactor.core.publisher.Mono;

public interface IEmailService<T extends IEmailService<T>> {

    Mono<Boolean> sendMail(EmailMessage emailMessage, Connection connection);
}
