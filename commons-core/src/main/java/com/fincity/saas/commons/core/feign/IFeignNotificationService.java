package com.fincity.saas.commons.core.feign;

import com.fincity.saas.commons.core.model.notification.NotificationRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "notification")
public interface IFeignNotificationService {

    String NOTIFICATION_PATH = "/api/notification"; // NOSONAR
    String NOTIFICATION_SENT = NOTIFICATION_PATH + "/sent";

    @PostMapping(NOTIFICATION_SENT)
    Mono<Boolean> sendNotification(@RequestBody NotificationRequest notification);
}
