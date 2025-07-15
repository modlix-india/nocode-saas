package com.fincity.saas.notification.controller;

import com.fincity.saas.notification.model.request.NotificationCacheRequest;
import com.fincity.saas.notification.service.NotificationCacheEvictionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/notification/caches")
public class NotificationCacheEvictionController {

    private final NotificationCacheEvictionService notificationConnectionService;

    public NotificationCacheEvictionController(NotificationCacheEvictionService notificationConnectionService) {
        this.notificationConnectionService = notificationConnectionService;
    }

    @PostMapping("/connections/evict")
    public Mono<Boolean> evictConnectionInfoCache(@RequestBody NotificationCacheRequest cacheRequest) {
        return this.notificationConnectionService.evictConnectionInfoCache(cacheRequest);
    }

    @PostMapping("/notifications/evict")
    public Mono<Boolean> evictNotificationInfoCache(@RequestBody NotificationCacheRequest cacheRequest) {
        return this.notificationConnectionService.evictNotificationInfoCache(cacheRequest);
    }
}
