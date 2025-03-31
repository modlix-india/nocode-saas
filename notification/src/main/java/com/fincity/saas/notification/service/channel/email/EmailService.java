package com.fincity.saas.notification.service.channel.email;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.notification.document.Connection;
import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import com.fincity.saas.notification.model.message.channel.EmailMessage;
import com.fincity.saas.notification.mq.action.service.AbstractMessageService;
import com.fincity.saas.notification.service.NotificationMessageResourceService;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class EmailService extends AbstractMessageService<EmailService> {

	private final SendGridService sendGridService;
	private final SMTPService smtpService;
	private final Map<String, IEmailService<?>> services = new HashMap<>();
	private final NotificationMessageResourceService msgService;

	public EmailService(SendGridService sendGridService, SMTPService smtpService,
	                    NotificationMessageResourceService msgService) {
		this.sendGridService = sendGridService;
		this.smtpService = smtpService;
		this.msgService = msgService;
	}

	@PostConstruct
	public void init() {
		this.services.put("sendgrid", sendGridService);
		this.services.put("smtp", smtpService);
	}

	@Override
	public Mono<Boolean> execute(Object message, Connection connection) {

		return FlatMapUtil.flatMapMono(

				() -> Mono.justOrEmpty(this.services.get(connection.getConnectionSubType().getProvider()))
						.switchIfEmpty(msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
								NotificationMessageResourceService.CONNECTION_DETAILS_MISSING,
								connection.getConnectionSubType())),

				service -> message instanceof EmailMessage emailMessage ? service.sendMail(emailMessage, connection)
						: Mono.just(Boolean.FALSE)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "EmailService.sendEmail"));
	}

	@Override
	public NotificationChannelType getChannelType() {
		return NotificationChannelType.EMAIL;
	}
}
