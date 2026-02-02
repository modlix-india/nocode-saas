package com.modlix.saas.notification.service.email;

import java.util.Map;

import com.modlix.saas.commons2.security.model.NotificationUser;
import com.modlix.saas.notification.model.CoreNotification;
import com.modlix.saas.notification.model.NotificationConnectionDetails;

public interface IAppEmailService {
    Boolean sendMail(
            NotificationUser user, NotificationConnectionDetails connection, CoreNotification notification, Map<String, Object> payload);
}
