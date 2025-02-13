package com.fincity.saas.notification.dao;

import static com.fincity.saas.notification.jooq.Tables.NOTIFICATION_NOTIFICATION;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.notification.dto.Notification;
import com.fincity.saas.notification.jooq.tables.records.NotificationNotificationRecord;

@Component
public class NotificationDao extends AbstractUpdatableDAO<NotificationNotificationRecord, ULong, Notification> {

	protected NotificationDao() {
		super(Notification.class, NOTIFICATION_NOTIFICATION, NOTIFICATION_NOTIFICATION.ID);
	}
}
