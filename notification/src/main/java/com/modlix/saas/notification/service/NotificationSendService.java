package com.modlix.saas.notification.service;

import java.util.ArrayList;
import java.util.List;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.modlix.saas.commons2.mq.notifications.NotificationQueObject;
import com.modlix.saas.commons2.security.feign.IFeignSecurityService;
import com.modlix.saas.commons2.security.model.NotificationUser;
import com.modlix.saas.commons2.security.model.UsersListRequest;
import com.modlix.saas.notification.feign.IFeignCoreService;
import com.modlix.saas.notification.model.CoreNotification;
import com.modlix.saas.notification.model.NotificationConnectionDetails;
import com.modlix.saas.notification.service.email.EmailService;

@Service
public class NotificationSendService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(NotificationSendService.class);

    private static final String NOTIFICATION_CHANNEL_IN_APP = "inapp";
    private static final String NOTIFICATION_CHANNEL_EMAIL = "email";

    public static final String USER_ID = "User Id";
    public static final String CLIENT_ID = "Client Id";
    public static final String CLIENT_CODE = "Client Code";

    private final IFeignCoreService coreService;

    private final IFeignSecurityService securityService;

    private final InAppNotificationService inAppService;

    private final EmailService emailService;

    public NotificationSendService(IFeignSecurityService securityService, IFeignCoreService coreService, EmailService emailService, InAppNotificationService inAppService) {

        this.securityService = securityService;
        this.coreService = coreService;
        this.emailService = emailService;
        this.inAppService = inAppService;
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

        List<NotificationUser> users;
        UsersListRequest request = new UsersListRequest();


        if (qob.getTargetType().equals(USER_ID)) {
            request.setUserIds(List.of(qob.getTargetId().longValue()));
        } else if (qob.getTargetType().equals(CLIENT_ID)) {
            request.setClientId(qob.getTargetId().longValue());
        } else if (qob.getTargetType().equals(CLIENT_CODE)) {
            request.setClientCode(qob.getTargetCode());
        }
        request.setAppCode(qob.getAppCode());
        users = this.securityService.getUsersForNotification(request);

        if (users.isEmpty()) {
            logger.error("Users not found: {}", qob);
            return;
        }

        if (isEmail) {
            for (NotificationUser user : users) {
                this.emailService.sendEmail(user, connection, notification, qob.getPayload());
            }
        }

        if (!isInApp) return;

        this.inAppService.sendInApp(users, notification, qob.getAppCode(), qob.getPayload());
    }
}
