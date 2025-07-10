package com.fincity.saas.commons.core.feign;

import com.fincity.saas.commons.core.model.notification.NotificationCacheRequest;
import com.fincity.saas.commons.core.model.notification.NotificationRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "notification")
public interface IFeignNotificationService {

    String NOTIFICATION_PATH = "/api/notification";

    String CACHE_EVICT = "/evict";

    String NOTIFICATION_SENT = NOTIFICATION_PATH + "/sent";

    String NOTIFICATION_CACHE_EVICT = NOTIFICATION_PATH + CACHE_EVICT;

    String NOTIFICATION_CONNECTION = NOTIFICATION_PATH + "/connections";

    String NOTIFICATION_CONNECTION_CACHE_EVICT = NOTIFICATION_CONNECTION + CACHE_EVICT;

    @PostMapping(NOTIFICATION_SENT)
    Mono<Boolean> sendNotification(@RequestBody NotificationRequest notification);

    @PostMapping(NOTIFICATION_CACHE_EVICT)
    Mono<Boolean> evictNotificationCache(@RequestBody NotificationCacheRequest notificationCacheRequest);

    @PostMapping(NOTIFICATION_CONNECTION_CACHE_EVICT)
    Mono<Boolean> evictNotificationConnectionCache(@RequestBody NotificationCacheRequest notificationCacheRequest);
}
