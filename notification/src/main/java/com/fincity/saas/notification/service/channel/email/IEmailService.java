package com.fincity.saas.notification.service.channel.email;

import com.fincity.saas.notification.document.Connection;
import com.fincity.saas.notification.model.message.channel.EmailMessage;
import reactor.core.publisher.Mono;

public interface IEmailService<T extends IEmailService<T>> {

    Mono<Boolean> sendMail(EmailMessage emailMessage, Connection connection);
}
