package com.fincity.saas.notification.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.notification.document.Notification;
import com.fincity.saas.notification.model.NotificationRequest;
import com.fincity.saas.notification.model.SendRequest;
import com.fincity.saas.notification.model.response.NotificationResponse;
import com.fincity.saas.notification.service.NotificationProcessingService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/notifications")
public class NotificationController
		implements INotificationCacheController<Notification, NotificationProcessingService> {

	private final NotificationProcessingService notificationProcessingService;

	public NotificationController(NotificationProcessingService notificationProcessingService) {
		this.notificationProcessingService = notificationProcessingService;
	}

	@Override
	public NotificationProcessingService getService() {
		return notificationProcessingService;
	}

	@PostMapping()
	public Mono<ResponseEntity<NotificationResponse>> sendNotification(@RequestBody NotificationRequest notification) {
		return this.notificationProcessingService.processAndSendNotification(notification)
				.map(ResponseEntity::ok);
	}

	@PostMapping("/process")
	public Mono<ResponseEntity<SendRequest>> processNotification(@RequestBody NotificationRequest notification) {
		return this.notificationProcessingService.processNotification(notification).map(ResponseEntity::ok);
	}

	@PostMapping("/send")
	public Mono<ResponseEntity<NotificationResponse>> sendNotification(@RequestBody SendRequest sendRequest) {
		return this.notificationProcessingService.sendNotification(sendRequest).map(ResponseEntity::ok);
	}
}
