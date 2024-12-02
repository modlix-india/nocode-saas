package com.fincity.saas.core.service.connection.email;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.core.document.Connection;
import com.fincity.saas.core.document.Template;
import com.fincity.saas.core.service.CoreMessageResourceService;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuples;

@Service
public class SMTPService extends AbstractEmailService implements IAppEmailService {

	@SuppressWarnings("unchecked")
	@Override
	public Mono<Boolean> sendMail(List<String> toAddresses, Template template, Map<String, Object> templateData,
			Connection connection) {

		if (connection.getConnectionDetails() == null)
			return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
					CoreMessageResourceService.MAIL_SEND_ERROR, "Connection details are missing");

		Map<String, Object> connProps = (Map<String, Object>) connection.getConnectionDetails()
				.get("mailProps");

		if (connProps == null || connProps.isEmpty())
			return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
					CoreMessageResourceService.MAIL_SEND_ERROR, "Connection Properties with 'mail.' are missing");

		if (StringUtil.safeIsBlank(connection.getConnectionDetails()
				.get("username")) || StringUtil.safeIsBlank(
						connection.getConnectionDetails()
								.get("password")))
			return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
					CoreMessageResourceService.MAIL_SEND_ERROR, "Connection username/password is missing");

		return FlatMapUtil.flatMapMono(

				() -> Mono.just(Tuples.of(toAddresses == null ? List.of() : toAddresses, template, templateData)),

				tup -> this.getProcessedEmailDetails(toAddresses, template, templateData),

				(tup, details) -> {

					try {
						Properties props = System.getProperties();
						for (var entry : connProps.entrySet())
							props.put(entry.getKey(), entry.getValue());

						Session session = Session.getDefaultInstance(props);

						MimeMessage message = new MimeMessage(session);

						message.setFrom(new InternetAddress(details.getFrom()));

						details.getTo()
								.forEach(e -> {
									try {
										message.addRecipient(Message.RecipientType.TO, new InternetAddress(e));
									} catch (MessagingException e1) {
										logger.error("Error while adding : {}", e, e1);
									}
								});
						message.setSubject(details.getSubject());
						message.setContent(details.getBody(), "text/html");

						Transport transport = session.getTransport();
						String user = StringUtil.safeValueOf(connection.getConnectionDetails()
								.get("username"));
						String password = StringUtil.safeValueOf(connection.getConnectionDetails()
								.get("password"));

						transport.connect(user, password);
						transport.sendMessage(message, message.getAllRecipients());

						return Mono.just(true);

					} catch (MessagingException mex) {

						logger.error("Error while sending : {}", mex.getMessage(), mex);

						return this.msgService.throwMessage(
								msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg, mex),
								CoreMessageResourceService.MAIL_SEND_ERROR, mex.getMessage());
					}
				})
				.map(e -> true)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "SMTPService.sendMail"));

	}

}
