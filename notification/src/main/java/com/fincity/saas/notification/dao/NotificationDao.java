package com.fincity.saas.notification.dao;

import static com.fincity.saas.notification.jooq.Tables.NOTIFICATION_NOTIFICATION;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.notification.dto.Notification;
import com.fincity.saas.notification.jooq.tables.records.NotificationNotificationRecord;

@Component
public class NotificationDao extends AbstractCodeDao<NotificationNotificationRecord, ULong, Notification> {

	protected NotificationDao() {
		super(Notification.class, NOTIFICATION_NOTIFICATION, NOTIFICATION_NOTIFICATION.ID, NOTIFICATION_NOTIFICATION.CODE);
	}
}
