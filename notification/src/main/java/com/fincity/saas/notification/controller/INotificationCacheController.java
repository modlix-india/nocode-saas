package com.fincity.saas.notification.controller;

import com.fincity.saas.notification.model.request.NotificationCacheRequest;
import com.fincity.saas.notification.service.INotificationCacheService;
import org.springframework.web.bind.annotation.PostMapping;
import reactor.core.publisher.Mono;

public interface INotificationCacheController<S, T extends INotificationCacheService<S>> {

    T getService();

    @PostMapping("/evict")
    default Mono<Boolean> evict(NotificationCacheRequest cacheRequest) {
        return this.getService().evict(cacheRequest);
    }
}
