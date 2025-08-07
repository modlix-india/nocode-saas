package com.fincity.saas.notification.dao;

import static com.fincity.saas.notification.jooq.tables.NotificationInAppNotifications.NOTIFICATION_IN_APP_NOTIFICATIONS;

import com.fincity.saas.notification.dto.InAppNotification;
import com.fincity.saas.notification.jooq.tables.records.NotificationInAppNotificationsRecord;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

@Component
public class InAppNotificationDao
        extends AbstractCodeDao<NotificationInAppNotificationsRecord, ULong, InAppNotification> {

    protected InAppNotificationDao() {
        super(
                InAppNotification.class,
                NOTIFICATION_IN_APP_NOTIFICATIONS,
                NOTIFICATION_IN_APP_NOTIFICATIONS.ID,
                NOTIFICATION_IN_APP_NOTIFICATIONS.CODE);
    }
}
