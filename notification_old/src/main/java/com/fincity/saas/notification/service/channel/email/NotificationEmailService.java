package com.fincity.saas.notification.service.channel.email;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import com.fincity.saas.notification.model.message.channel.EmailMessage;
import com.fincity.saas.notification.model.request.SendRequest;
import com.fincity.saas.notification.mq.action.service.AbstractMessageService;
import com.fincity.saas.notification.oserver.core.document.Connection;
import com.fincity.saas.notification.oserver.core.enums.ConnectionSubType;
import com.fincity.saas.notification.service.NotificationMessageResourceService;
import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class NotificationEmailService extends AbstractMessageService<NotificationEmailService> {

    private final SendGridService sendGridService;
    private final SMTPService smtpService;
    private final Map<ConnectionSubType, IEmailService<?>> services = new EnumMap<>(ConnectionSubType.class);
    private final NotificationMessageResourceService msgService;

    public NotificationEmailService(
            SendGridService sendGridService, SMTPService smtpService, NotificationMessageResourceService msgService) {
        this.sendGridService = sendGridService;
        this.smtpService = smtpService;
        this.msgService = msgService;
    }

    @PostConstruct
    public void init() {
        this.services.put(ConnectionSubType.SENDGRID, sendGridService);
        this.services.put(ConnectionSubType.SMTP, smtpService);
    }

    @Override
    public Mono<Boolean> execute(SendRequest request, Connection connection) {

        return FlatMapUtil.flatMapMono(
                        () -> Mono.justOrEmpty(this.services.get(connection.getConnectionSubType()))
                                .switchIfEmpty(msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                                        NotificationMessageResourceService.CONNECTION_DETAILS_MISSING,
                                        connection.getConnectionSubType())),
                        service -> {
                            EmailMessage emailMessage = request.getChannels().get(this.getChannelType());
                            if (emailMessage != null) return service.sendMail(emailMessage, connection);
                            return Mono.just(Boolean.FALSE);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "EmailService.execute"));
    }

    @Override
    public NotificationChannelType getChannelType() {
        return NotificationChannelType.EMAIL;
    }
}
