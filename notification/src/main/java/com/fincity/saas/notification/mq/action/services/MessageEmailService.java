package com.fincity.saas.notification.mq.action.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.saas.notification.enums.NotificationChannelType;
import com.fincity.saas.notification.model.SendRequest;
import com.fincity.saas.notification.service.email.EmailService;

import reactor.core.publisher.Mono;

@Service
public class MessageEmailService extends AbstractMessageService implements IMessageService {

	private EmailService emailService;

	@Autowired
	private void setEmailService(EmailService emailService) {
		this.emailService = emailService;
	}

	@Override
	public NotificationChannelType getChannelType() {
		return NotificationChannelType.EMAIL;
	}

	@Override
	public Mono<Boolean> execute(SendRequest request) {

		if (!super.isValid(request))
			return Mono.just(Boolean.FALSE);

		return super.getConnection(request.getAppCode(), request.getClientCode(), request.getConnections().get(this.getChannelType()))
				.flatMap(connection -> this.emailService.sendEmail(request.getChannels().getEmail(), connection));
	}
}
