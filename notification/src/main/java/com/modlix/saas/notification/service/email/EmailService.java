package com.modlix.saas.notification.service.email;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.modlix.saas.commons2.security.model.NotificationUser;
import com.modlix.saas.notification.model.CoreNotification;
import com.modlix.saas.notification.model.NotificationConnectionDetails;

import jakarta.annotation.PostConstruct;

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

    public Boolean sendEmail(NotificationUser user, NotificationConnectionDetails connection, CoreNotification notification, Map<String, Object> payload) {

        return this.services.get(connection.getMail().getConnectionSubType())
                    .sendMail(user, connection, notification, payload);
    }
}
