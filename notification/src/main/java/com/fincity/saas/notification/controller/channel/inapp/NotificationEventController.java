package com.fincity.saas.notification.controller.channel.inapp;

import java.math.BigInteger;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.notification.model.message.channel.InAppMessage;
import com.fincity.saas.notification.model.response.SendResponse;
import com.fincity.saas.notification.service.channel.inapp.NotificationEventService;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("api/notifications/inApp/events")
public class NotificationEventController {

	private final NotificationEventService notificationEventService;

	public NotificationEventController(NotificationEventService notificationEventService) {
		this.notificationEventService = notificationEventService;
	}

	@GetMapping(path = "/subscribe/{appCode}/{clientCode}/{userId}",
			produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<SendResponse<InAppMessage>>> subscribeToEvent(
			@PathVariable String appCode,
			@PathVariable String clientCode,
			@PathVariable BigInteger userId) {
		return notificationEventService.subscribeToEvent(appCode, clientCode, userId);
	}
}
