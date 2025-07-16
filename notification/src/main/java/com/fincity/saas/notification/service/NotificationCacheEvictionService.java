package com.fincity.saas.notification.service;

import com.fincity.saas.notification.model.request.NotificationCacheRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class NotificationCacheEvictionService {

    private NotificationProcessingService notificationProcessingService;

    @Lazy
    @Autowired
    private void setNotificationProcessingService(NotificationProcessingService notificationProcessingService) {
        this.notificationProcessingService = notificationProcessingService;
    }

    public Mono<Boolean> evictNotificationInfoCache(NotificationCacheRequest cacheRequest) {

        if (cacheRequest == null || !cacheRequest.hasChannelEntities()) return Mono.just(Boolean.FALSE);

        return this.notificationProcessingService.evictChannelEntities(cacheRequest.getChannelEntities());
    }
}
