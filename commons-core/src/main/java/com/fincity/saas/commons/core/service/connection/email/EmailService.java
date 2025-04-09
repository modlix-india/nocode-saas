package com.fincity.saas.commons.core.service.connection.email;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.enums.ConnectionSubType;
import com.fincity.saas.commons.core.enums.ConnectionType;
import com.fincity.saas.commons.core.service.ConnectionService;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.core.service.TemplateService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class EmailService {

    private final ConnectionService connectionService;

    private final CoreMessageResourceService msgService;

    private final SendGridService sendGridService;
    private final SMTPService smtpService;

    private final TemplateService templateService;

    private final EnumMap<ConnectionSubType, IAppEmailService> services = new EnumMap<>(ConnectionSubType.class);

    public EmailService(
            SendGridService sendGridService,
            SMTPService smtpService,
            TemplateService templateService,
            ConnectionService connectionService,
            CoreMessageResourceService msgService) {
        this.sendGridService = sendGridService;
        this.smtpService = smtpService;
        this.templateService = templateService;
        this.connectionService = connectionService;
        this.msgService = msgService;
    }

    @PostConstruct
    public void init() {
        this.services.put(ConnectionSubType.SENDGRID, sendGridService);
        this.services.put(ConnectionSubType.SMTP, smtpService);
    }

    public Mono<Boolean> sendEmail(
            String appCode,
            String clientCode,
            List<String> addresses,
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
                                        templateName)),
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
