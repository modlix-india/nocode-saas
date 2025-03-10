package com.fincity.saas.core.feign;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.fincity.saas.core.model.NotificationRequest;

import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "notifications")
public interface IFeignNotificationService {

	String NOTIFICATION_PATH = "api/notifications";

	String NOTIFICATION_SENT = NOTIFICATION_PATH + "/sent";

	@PostMapping(NOTIFICATION_SENT)
	Mono<Boolean> sendNotification(@RequestBody NotificationRequest notification);

}
