package com.fincity.saas.notification.dao;

import static com.fincity.saas.notification.jooq.tables.NotificationType.NOTIFICATION_TYPE;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.notification.dto.NotificationType;
import com.fincity.saas.notification.jooq.tables.records.NotificationTypeRecord;

@Component
public class NotificationTypeDao extends AbstractUpdatableDAO<NotificationTypeRecord, ULong, NotificationType> {

	protected NotificationTypeDao() {
		super(NotificationType.class, NOTIFICATION_TYPE, NOTIFICATION_TYPE.ID);
	}
}
