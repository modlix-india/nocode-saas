package com.fincity.saas.notification.dao;

import static com.fincity.saas.notification.jooq.tables.NotificationSentNotifications.NOTIFICATION_SENT_NOTIFICATIONS;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.notification.dto.SentNotification;
import com.fincity.saas.notification.jooq.tables.records.NotificationSentNotificationsRecord;

@Component
public class SentNotificationDao extends AbstractCodeDao<NotificationSentNotificationsRecord, ULong, SentNotification> {

	protected SentNotificationDao() {
		super(SentNotification.class, NOTIFICATION_SENT_NOTIFICATIONS, NOTIFICATION_SENT_NOTIFICATIONS.ID,
				NOTIFICATION_SENT_NOTIFICATIONS.CODE);
	}
}
