package com.fincity.saas.notification.service.email;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.notification.model.message.EmailMessage;
import com.fincity.saas.notification.service.NotificationMessageResourceService;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class EmailService {

	private final SendGridService sendGridService;
	private final SMTPService smtpService;
	private final Map<String, IEmailService> services = new HashMap<>();
	private final NotificationMessageResourceService msgService;

	public EmailService(SendGridService sendGridService, SMTPService smtpService, NotificationMessageResourceService msgService) {
		this.sendGridService = sendGridService;
		this.smtpService = smtpService;
		this.msgService = msgService;
	}

	@PostConstruct
	public void init() {
		this.services.put("sendgrid", sendGridService);
		this.services.put("smtp", smtpService);
	}

	public Mono<Boolean> sendEmail(EmailMessage emailMessage, Map<String, Object> connection) {

		return FlatMapUtil.flatMapMono(

				() -> this.getEmailProvider(connection.get("connectionSubType").toString()),

				provider -> Mono.justOrEmpty(this.services.get(provider))
						.switchIfEmpty(msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
								NotificationMessageResourceService.CONNECTION_DETAILS_MISSING, provider)),

				(provider, service) -> service.sendMail(emailMessage, connection)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "EmailService.sendEmail"));
	}

	private Mono<String> getEmailProvider(String connectionSubType) {
		String[] parts = connectionSubType.split("_");
		return Mono.justOrEmpty(parts[parts.length - 1].toLowerCase());
	}
}
