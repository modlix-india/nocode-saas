package com.modlix.saas.notification.service.email;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.modlix.saas.commons2.security.model.NotificationUser;
import com.modlix.saas.commons2.util.StringUtil;
import com.modlix.saas.notification.model.CoreNotification;
import com.modlix.saas.notification.model.NotificationConnectionDetails;

@Service
public class SendGridService extends AbstractEmailService implements IAppEmailService {

    @Override
    public Boolean sendMail(
        NotificationUser user, NotificationConnectionDetails connection, CoreNotification notification, Map<String, Object> payload) {
        if (connection.getMail().getConnectionDetails() == null
                || StringUtil.safeIsBlank(connection.getMail().getConnectionDetails().get("apiKey")))
                return false;

        String apiKey = connection.getMail().getConnectionDetails().get("apiKey").toString();

        String fromAddress = StringUtil.safeValueOf(connection.getMail().getConnectionDetails().get("fromAddress"));

        if (StringUtil.safeIsBlank(fromAddress))
            return false;

        String language = this.getLanguage(notification, payload);

        Map<String, String> template = this.getProcessedTemplate(language, notification.getChannelTemplates().get(EMAIL_TEMPLATE_NAME), payload);

        if (template == null || template.isEmpty())
            return false;

        return RestClient.create()
                .post()
                .uri("https://api.sendgrid.com/v3/mail/send")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(this.getSendGridBody(user.getEmailId(), fromAddress, template.get("subject"), template.get("body")))
                .retrieve()
                .body(String.class) != null;
    }

    private Map<String, Object> getSendGridBody(String to, String from, String subject, String body) {
        return Map.of(
                "personalizations",
                List.of(Map.of(
                        "to",
                        List.of(Map.of("email", to)))),
                "from",
                Map.of("email", from),
                "subject",
                subject,
                "content",
                List.of(Map.of("type", "text/html", "value", body)));
    }
}
