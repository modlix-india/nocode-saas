package com.fincity.saas.notification.service.email;

import java.util.Map;

import com.fincity.saas.notification.model.message.EmailMessage;

import reactor.core.publisher.Mono;

public interface IEmailService {

	Mono<Boolean> sendMail(EmailMessage emailMessage, Map<String, Object> connection);

}
