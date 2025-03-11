package com.fincity.saas.notification.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.notification.service.NotificationConnectionService;

@RestController
@RequestMapping("api/notifications/connections")
public class NotificationConnectionController implements INotificationCacheController<Map<String, Object>, NotificationConnectionService> {

	private final NotificationConnectionService notificationConnectionService;

	public NotificationConnectionController(NotificationConnectionService notificationConnectionService) {
		this.notificationConnectionService = notificationConnectionService;
	}

	@Override
	public NotificationConnectionService getService() {
		return notificationConnectionService;
	}
}
