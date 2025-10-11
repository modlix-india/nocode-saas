package com.modlix.saas.notification.service.email;

import jakarta.annotation.PostConstruct;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class EmailService {

    private final SendGridService sendGridService;
    private final SMTPService smtpService;

    private final Map<String, IAppEmailService> services = new HashMap<>();

    public EmailService(SendGridService sendGridService, SMTPService smtpService) {
        this.sendGridService = sendGridService;
        this.smtpService = smtpService;
    }

    @PostConstruct
    public void init() {
        this.services.put("SENDGRID", sendGridService);
        this.services.put("SMTP", smtpService);
    }

    public Boolean sendEmail(
            String appCode,
            String clientCode,
            String address,
            String templateName,
            Map<String, Object> templateData) {
        return this.sendEmail(appCode, clientCode, addresses, templateName, null, templateData);
    }

    public Mono<Boolean> sendEmail(
            String appCode,
            String clientCode,
            List<String> addresses,
            String templateName,
            String connectionName,
            Map<String, Object> templateData) {
        return FlatMapUtil.flatMapMono(
                        () -> SecurityContextUtil.resolveAppAndClientCode(appCode, clientCode),
                        actup -> connectionService
                                .read(connectionName, actup.getT1(), actup.getT2(), ConnectionType.MAIL)
                                .switchIfEmpty(msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                        CoreMessageResourceService.CONNECTION_DETAILS_MISSING,
                                        connectionName)),
                        (actup, conn) -> Mono.justOrEmpty(this.services.get(conn.getConnectionSubType()))
                                .switchIfEmpty(msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                        CoreMessageResourceService.CONNECTION_DETAILS_MISSING,
                                        conn.getConnectionSubType())),
                        (actup, conn, mailService) -> templateService
                                .read(templateName, actup.getT1(), actup.getT2())
                                .map(ObjectWithUniqueID::getObject)
                                .switchIfEmpty(msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                        CoreMessageResourceService.TEMPLATE_DETAILS_MISSING,
                                        templateName)),
                        (actup, conn, mailService, template) ->
                                mailService.sendMail(addresses, template, templateData, conn))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "EmailService.sendEmail"));
    }
}
