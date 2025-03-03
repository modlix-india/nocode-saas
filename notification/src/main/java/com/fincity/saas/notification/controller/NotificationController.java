package com.fincity.saas.notification.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.notification.model.NotificationRequest;
import com.fincity.saas.notification.model.SendRequest;
import com.fincity.saas.notification.service.NotificationProcessingService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/notifications")
public class NotificationController {

	private final NotificationProcessingService notificationProcessingService;

	public NotificationController(NotificationProcessingService notificationProcessingService) {
		this.notificationProcessingService = notificationProcessingService;
	}

	@PostMapping("/process")
	public Mono<ResponseEntity<SendRequest>> processNotification(@RequestBody NotificationRequest notification) {
		return this.notificationProcessingService.processNotification(notification).map(ResponseEntity::ok);
	}

	@PostMapping("/sent")
	public Mono<ResponseEntity<Boolean>> sendNotification(@RequestBody SendRequest sendRequest) {
		return this.notificationProcessingService.sendNotification(sendRequest).map(ResponseEntity::ok);
	}

	@PostMapping("/sent")
	public Mono<ResponseEntity<Boolean>> sendNotification(@RequestBody NotificationRequest notification) {
		return this.notificationProcessingService.processAndSendNotification(notification)
				.map(ResponseEntity::ok);
	}
}
