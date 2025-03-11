package com.fincity.saas.notification.service.email;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.notification.document.Connection;
import com.fincity.saas.notification.model.message.EmailMessage;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

@Service(value = "smtp")
public class SMTPService extends AbstractEmailService implements IEmailService {

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Boolean> sendMail(EmailMessage emailMessage, Connection connection) {

		Map<String, Object> connectionDetails = connection.getConnectionDetails();

		if (connectionDetails.isEmpty())
			return Mono.just(Boolean.FALSE);

		Map<String, Object> connProps = (Map<String, Object>) connectionDetails.get("mailProps");

		if (connProps == null || connProps.isEmpty())
			return this.throwMailSendError("Connection Properties with 'mail.' are missing");

		String username = StringUtil.safeValueOf(connectionDetails.get("username"), "");
		String password = StringUtil.safeValueOf(connectionDetails.get("password"), "");

		if (StringUtil.safeIsBlank(username) || StringUtil.safeIsBlank(password))
			return this.throwMailSendError("Connection username/password is missing");

		return FlatMapUtil.flatMapMono(

						() -> this.hasValidConnection(connection),

						isValidConnection -> this.sendMailWithSession(emailMessage, connProps, username, password))
				.onErrorResume(ex -> {
					logger.error("Error while sending email: {}", ex.getMessage(), ex);
					return this.throwMailSendError(ex.getMessage());
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "SMTPService.sendMail"));
	}

	private Mono<Boolean> sendMailWithSession(EmailMessage emailMessage, Map<String, Object> connectionProps,
	                                          String username, String password) {

		return Mono.fromCallable(() -> {
					Properties props = System.getProperties();

					props.putAll(connectionProps);

					Session session = Session.getDefaultInstance(props);

					MimeMessage message = this.createMimeMessage(session, emailMessage);

					Transport transport = session.getTransport();

					transport.connect(username, password);
					transport.sendMessage(message, message.getAllRecipients());

					return Boolean.TRUE;
				}).subscribeOn(Schedulers.boundedElastic())
				.onErrorResume(IOException.class, ex -> {
					logger.error("Error while sending SMTP email: {}", ex.getMessage(), ex);
					return this.throwMailSendError(ex.getMessage());
				});
	}

	private MimeMessage createMimeMessage(Session session, EmailMessage emailMessage) throws MessagingException {

		MimeMessage message = new MimeMessage(session);

		message.setFrom(new InternetAddress(emailMessage.getFromAddress()));
		message.addRecipient(Message.RecipientType.TO, new InternetAddress(emailMessage.getToAddress()));

		this.addMessageRecipients(message, Message.RecipientType.BCC, emailMessage.getBccAddresses());
		this.addMessageRecipients(message, Message.RecipientType.CC, emailMessage.getCcAddresses());

		message.setReplyTo(this.getInternetAddresses(emailMessage.getReplyTo()));

		message.setSubject(emailMessage.getSubject());

		MimeBodyPart mimeBodyPart = new MimeBodyPart();
		mimeBodyPart.setContent(emailMessage.getBody(), "text/html; charset=utf-8");
		mimeBodyPart.setContentID(this.generateContentId(emailMessage.getFromAddress()));

		Multipart multipart = new MimeMultipart();
		multipart.addBodyPart(mimeBodyPart);

		message.setContent(multipart);
		message.setSentDate(new Date());

		this.addMessageHeader(message, emailMessage.getHeaders());

		return message;
	}

	private void addMessageRecipients(Message message, Message.RecipientType type, List<String> addresses) {

		if (addresses == null || addresses.isEmpty())
			return;

		addresses.stream().filter(Objects::nonNull).forEach(address -> {
			try {
				message.setRecipient(type, this.getInternetAddress(address));
			} catch (MessagingException e) {
				logger.error("Error while sending : {}", e.getMessage(), e);
			}
		});
	}

	private void addMessageHeader(Message message, Map<String, String> headers) {

		if (headers == null || headers.isEmpty())
			return;

		headers.forEach((name, value) -> this.addMessageHeader(message, name, value));
	}

	private void addMessageHeader(Message message, String name, String value) {
		try {
			message.addHeader(name, value);
		} catch (MessagingException e) {
			logger.error("Error while adding header : {}:{}", name, value);
		}
	}

	private Address[] getInternetAddresses(List<String> addresses) {
		return addresses.stream().map(this::getInternetAddress).toArray(Address[]::new);
	}

	private Address getInternetAddress(String address) {
		try {
			return new InternetAddress(address);
		} catch (AddressException addressException) {
			logger.error("Error while getting Internet Address: {}", addressException.getMessage());
			return null;
		}
	}

}
