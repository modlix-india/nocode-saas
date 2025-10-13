package com.modlix.saas.notification.service.email;

import java.util.Map;
import java.util.Properties;

import org.springframework.stereotype.Service;

import com.modlix.saas.commons2.security.model.NotificationUser;
import com.modlix.saas.commons2.util.StringUtil;
import com.modlix.saas.notification.model.CoreNotification;
import com.modlix.saas.notification.model.NotificationConnectionDetails;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

@Service
public class SMTPService extends AbstractEmailService implements IAppEmailService {

    @SuppressWarnings("unchecked")
    public Boolean sendMail(NotificationUser user, NotificationConnectionDetails notificationConnection, CoreNotification notification, Map<String, Object> payload) {

        if (user == null || user.getEmailId() == null || user.getEmailId().isBlank() || "NONE".equalsIgnoreCase(user.getEmailId()) 
            || user.getEmailId().indexOf('@') == -1)
            return false;
        
        Map<String, Object> connectionDetails = notificationConnection.getMail().getConnectionDetails();
        if (connectionDetails == null || connectionDetails.isEmpty())
            return false;

        Map<String, Object> connProps =
                (Map<String, Object>) connectionDetails.get("mailProps");
        if (connProps == null || connProps.isEmpty())
            return false;

        String userName = StringUtil.safeValueOf(connectionDetails.get("username"));
        String password = StringUtil.safeValueOf(connectionDetails.get("password"));

        if (StringUtil.safeIsBlank(userName)
                || StringUtil.safeIsBlank(password))
            return false; 

        String fromAddress = StringUtil.safeValueOf(connectionDetails.get("fromAddress"));

        if (StringUtil.safeIsBlank(fromAddress))
            return false;

        String language = this.getLanguage(notification, payload);

        Map<String, String> template = this.getProcessedTemplate(language, notification.getChannelTemplates().get(EMAIL_TEMPLATE_NAME), payload);

        if (template == null || template.isEmpty())
            return false;

        Properties props = new Properties();
        props.putAll(connProps);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(userName, password);
            }
        });

        MimeMessage message = new MimeMessage(session);

        try {
            message.setFrom(new InternetAddress(fromAddress));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(user.getEmailId()));
            message.setSubject(template.get("subject"));
            message.setContent(template.get("body"), "text/html");

            Transport.send(message, message.getAllRecipients());
        } catch (MessagingException e) {
            logger.error("Error while adding : {}", user.getEmailId(), e);
        }
        
        return true;
    }
}
