package com.fincity.saas.core.service.connection.email;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.core.enums.ConnectionSubType;
import com.fincity.saas.core.enums.ConnectionType;
import com.fincity.saas.core.service.ConnectionService;
import com.fincity.saas.core.service.CoreMessageResourceService;
import com.fincity.saas.core.service.TemplateService;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuples;

@Service
public class EmailService {

	@Autowired
	private ConnectionService connectionService;

	@Autowired
	private CoreMessageResourceService msgService;

	@Autowired
	private SendGridService sendGridService;

	@Autowired
	private SMTPService smtpService;

	@Autowired
	private TemplateService templateService;

	private final EnumMap<ConnectionSubType, IAppEmailService> services = new EnumMap<>(ConnectionSubType.class);

	@PostConstruct
	public void init() {

		this.services.put(ConnectionSubType.SENDGRID, sendGridService);
		this.services.put(ConnectionSubType.SMTP, smtpService);
	}

	public Mono<Boolean> sendEmail(String appCode, String clientCode, List<String> addresses, String templateName,
			Map<String, Object> templateData) {
		return this.sendEmail(appCode, clientCode, addresses, templateName, null, templateData);
	}

	public Mono<Boolean> sendEmail(String appCode, String clientCode, List<String> addresses, String templateName,
			String connectionName, Map<String, Object> templateData) {

		return FlatMapUtil.flatMapMono(

				() -> {

					String inAppCode = appCode.trim().isEmpty() ? null : appCode;
					String inClientCode = clientCode.trim().isEmpty() ? null : clientCode;

					return SecurityContextUtil.getUsersContextAuthentication()
							.map(e -> Tuples.of(
									CommonsUtil.nonNullValue(inAppCode, e.getUrlAppCode()),
									CommonsUtil.nonNullValue(inClientCode, e.getClientCode())))
							.defaultIfEmpty(Tuples.of(inAppCode, inClientCode));
				},

				actup -> connectionService.read(connectionName, actup.getT1(), actup.getT2(), ConnectionType.MAIL)
						.switchIfEmpty(msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
								CoreMessageResourceService.CONNECTION_DETAILS_MISSING,
								templateName)),

				(actup, conn) -> Mono.justOrEmpty(this.services.get(conn.getConnectionSubType()))
						.switchIfEmpty(
								msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
										CoreMessageResourceService.CONNECTION_DETAILS_MISSING,
										conn.getConnectionSubType())),

				(actup, conn, mailService) -> templateService.read(templateName, actup.getT1(), actup.getT2())
						.map(ObjectWithUniqueID::getObject)
						.switchIfEmpty(msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
								CoreMessageResourceService.TEMPLATE_DETAILS_MISSING,
								templateName)),

				(actup, conn, mailService, template) -> mailService.sendMail(addresses, template, templateData, conn)

		)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "EmailService.sendEmail"));
	}
}
