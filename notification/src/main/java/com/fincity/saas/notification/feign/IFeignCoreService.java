package com.fincity.saas.notification.feign;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fincity.saas.notification.document.Notification;

import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "core")
public interface IFeignCoreService {

	String CONNECTION_PATH = "api/core/connections/internal";

	String NOTIFICATION_PATH = "api/core/notifications/internal";

	@GetMapping(CONNECTION_PATH)
	Mono<Map<String, Object>> getConnection(
			@RequestParam String connectionName,
			@RequestParam String appCode,
			@RequestParam String clientCode,
			@RequestParam String connectionType);

	@GetMapping(NOTIFICATION_PATH)
	Mono<Notification> getNotificationInfo(
			@RequestParam String notificationName,
			@RequestParam String appCode,
			@RequestParam String clientCode
	);
}
