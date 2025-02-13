package com.fincity.saas.notification.service;

import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.notification.dao.NotificationDao;
import com.fincity.saas.notification.dto.Notification;
import com.fincity.saas.notification.jooq.tables.records.NotificationNotificationRecord;

import reactor.core.publisher.Mono;

@Service
public class NotificationService extends
		AbstractJOOQUpdatableDataService<NotificationNotificationRecord, ULong, Notification, NotificationDao> {

	@Override
	protected Mono<Notification> updatableEntity(Notification entity) {
		return null;
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {
		return null;
	}
}
