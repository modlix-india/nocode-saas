package com.fincity.saas.notification.service.email;

import com.fincity.saas.notification.model.NotificationTemplate;
import com.fincity.saas.notification.model.message.EmailMessage;

import reactor.core.publisher.Mono;

public interface IEmailService {

	Mono<Boolean> sendMail(EmailMessage emailMessage, NotificationTemplate template);

}
