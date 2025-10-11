package com.modlix.saas.notification.service;

import com.modlix.saas.commons2.mq.notifications.NotificationQueObject;
import com.modlix.saas.commons2.security.feign.IFeignSecurityService;
import com.modlix.saas.commons2.security.util.LogUtil;
import com.modlix.saas.notification.feign.IFeignCoreService;
import com.modlix.saas.notification.model.NotificationConnectionDetails;
import com.modlix.saas.notification.model.CoreNotification;
import com.modlix.saas.notification.service.email.EmailService;

import org.springframework.stereotype.Service;

@Service
public class NotificationSendService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(NotificationSendService.class);

    private static final String NOTIFICATION_CHANNEL_IN_APP = "inapp";
    private static final String NOTIFICATION_CHANNEL_EMAIL = "email";

    private final IFeignCoreService coreService;

    private final IFeignSecurityService securityService;

    private final EmailService emailService;

    public NotificationSendService(IFeignSecurityService securityService, IFeignCoreService coreService, EmailService emailService) {

        this.securityService = securityService;
        this.coreService = coreService;
        this.emailService = emailService;
    }

    public void sendNotification(NotificationQueObject qob) {

        NotificationConnectionDetails connection = this.coreService.getNotificationConnection(qob.getConnectionName(), qob.getAppCode(), qob.getClientCode(), qob.getUrlClientCode());

        if (connection == null) {
            logger.error("Notification connection not found: {}", qob.getConnectionName());
            return;
        }

        CoreNotification notification = this.coreService.getNotification(qob.getNotificationName(), qob.getAppCode(), qob.getClientCode(), qob.getUrlClientCode());

        if (notification == null) {
            logger.error("Notification not found: {}", qob.getNotificationName());
            return;
        }

        boolean isEmail = connection.getMail() != null && notification.getChannelTemplates().containsKey(NOTIFICATION_CHANNEL_EMAIL);
        boolean isInApp = connection.isInApp() && notification.getChannelTemplates().containsKey(NOTIFICATION_CHANNEL_IN_APP);

        if (!isEmail && !isInApp) {
            logger.error("Notification channel and templates are not found: {}", qob);
            return;
        }

        
    }
}
