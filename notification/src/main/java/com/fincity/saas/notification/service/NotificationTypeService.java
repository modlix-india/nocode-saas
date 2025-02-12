package com.fincity.saas.notification.service;

import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.notification.dao.NotificationTypeDao;
import com.fincity.saas.notification.dto.NotificationType;
import com.fincity.saas.notification.jooq.tables.records.NotificationTypeRecord;

import lombok.Getter;
import reactor.core.publisher.Mono;

@Getter
@Service
public class NotificationTypeService extends AbstractJOOQUpdatableDataService<NotificationTypeRecord, ULong, NotificationType, NotificationTypeDao> {

	private NotificationMessageResourceService messageResourceService;

	private CacheService cacheService;

	@Autowired
	public void setMessageResourceService(NotificationMessageResourceService messageResourceService) {
		this.messageResourceService = messageResourceService;
	}

	@Autowired
	public void setCacheService(CacheService cacheService) {
		this.cacheService = cacheService;
	}

	@Override
	protected Mono<NotificationType> updatableEntity(NotificationType entity) {
		return null;
	}

	@Override
	protected Mono<Map<String, Object>> updatableFields(ULong key, Map<String, Object> fields) {
		return null;
	}
}
