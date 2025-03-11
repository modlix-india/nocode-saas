package com.fincity.saas.notification.service.email;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.notification.document.Connection;
import com.fincity.saas.notification.model.message.EmailMessage;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

@Service(value = "sendgrid")
public class SendGridService extends AbstractEmailService implements IEmailService {

	private static final String EMAIL_ENDPOINT = "mail/send";

	private static final String API_KEY = "apiKey";

	private final ConcurrentHashMap<String, SendGrid> sendGridClients = new ConcurrentHashMap<>();

	@Override
	public Mono<Boolean> sendMail(EmailMessage emailMessage, Connection connection) {

		Map<String, Object> connectionDetails = connection.getConnectionDetails();

		if (connectionDetails.isEmpty())
			return Mono.just(Boolean.FALSE);

		if (StringUtil.safeIsBlank(connectionDetails.get(API_KEY)))
			return this.throwMailSendError("SENDGRID api key is not found");

		String apiKey = connectionDetails.get(API_KEY).toString();

		return FlatMapUtil.flatMapMono(

				() -> this.hasValidConnection(connection),

				isValidConnection -> this.callSendGrid(emailMessage, apiKey)
		).onErrorResume(ex -> {
			logger.error("Error while sending sendgrid email: {}", ex.getMessage(), ex);
			return this.throwMailSendError(ex.getMessage());
		}).contextWrite(Context.of(LogUtil.METHOD_NAME, "SendGridService.sendMail"));

	}

	private Mono<Boolean> callSendGrid(EmailMessage emailMessage, String apiKey) {

		return Mono.fromCallable(() -> {

					SendGrid sendGrid = sendGridClients.computeIfAbsent(apiKey, SendGrid::new);
					Mail mail = this.buidMail(emailMessage);
					Request request = new Request();
					request.setMethod(Method.POST);
					request.setEndpoint(EMAIL_ENDPOINT);
					request.setBody(mail.build());

					return sendGrid.api(request);
				}).subscribeOn(Schedulers.boundedElastic())
				.map(response -> HttpStatus.valueOf(response.getStatusCode()).is2xxSuccessful())
				.onErrorResume(IOException.class, ex -> {
					logger.error("Error while sending sendgrid email: {}", ex.getMessage(), ex);
					return this.throwMailSendError(ex.getMessage());
				});

	}

	private Mail buidMail(EmailMessage emailMessage) {

		Mail mail = new Mail();
		mail.setFrom(new Email(emailMessage.getFromAddress()));

		Content content = new Content("text/html", emailMessage.getBody());

		mail.addContent(content);

		Personalization personalization = new Personalization();

		personalization.addTo(new Email(emailMessage.getToAddress()));
		emailMessage.getBccAddresses().forEach(bcc -> personalization.addBcc(new Email(bcc)));
		emailMessage.getCcAddresses().forEach(cc -> personalization.addCc(new Email(cc)));
		emailMessage.getHeaders().forEach(personalization::addHeader);
		personalization.setSubject(emailMessage.getSubject());

		mail.addPersonalization(personalization);
		mail.setReplyTo(new Email(emailMessage.getReplyTo().getFirst()));
		return mail;
	}

}
