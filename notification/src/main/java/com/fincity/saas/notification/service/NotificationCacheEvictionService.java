package com.fincity.saas.notification.service;

import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.notification.model.request.NotificationCacheRequest;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class NotificationCacheEvictionService {

    private CacheService cacheService;

    private NotificationProcessingService notificationProcessingService;

    private NotificationConnectionService notificationConnectionService;

    @Autowired
    private void setCacheService(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Lazy
    @Autowired
    private void setNotificationProcessingService(NotificationProcessingService notificationProcessingService) {
        this.notificationProcessingService = notificationProcessingService;
    }

    @Lazy
    @Autowired
    private void setNotificationConnectionService(NotificationConnectionService notificationConnectionService) {
        this.notificationConnectionService = notificationConnectionService;
    }

    protected String getCacheKey(Object... entityNames) {
        return String.join(":", Stream.of(entityNames).map(Object::toString).toArray(String[]::new));
    }

    protected String getCacheKey(String... entityNames) {
        return String.join(":", entityNames);
    }

    private String getCacheKey(NotificationCacheRequest cacheRequest) {
        return this.getCacheKey(cacheRequest.getAppCode(), cacheRequest.getClientCode(), cacheRequest.getEntityName());
    }

    public Mono<Boolean> evictConnectionInfoCache(NotificationCacheRequest cacheRequest) {

        if (cacheRequest.isEmpty()) return Mono.just(Boolean.FALSE);

        return this.cacheService.evict(
                this.notificationConnectionService.getCacheName(), this.getCacheKey(cacheRequest));
    }

    public Mono<Boolean> evictNotificationInfoCache(NotificationCacheRequest cacheRequest) {

        if (cacheRequest.isEmpty()) return Mono.just(Boolean.FALSE);

        if (cacheRequest.hasChannelEntities())
            return Mono.zip(
                    this.notificationProcessingService.evictChannelEntities(cacheRequest.getChannelEntities()),
                    this.cacheService.evict(
                            this.notificationProcessingService.getCacheName(), this.getCacheKey(cacheRequest)),
                    (channelEntriesEvicted, notificationInfoEvicted) ->
                            channelEntriesEvicted && notificationInfoEvicted);

        return this.cacheService.evict(
                this.notificationProcessingService.getCacheName(), this.getCacheKey(cacheRequest));
    }
}
