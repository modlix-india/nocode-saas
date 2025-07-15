package com.fincity.saas.notification.service;

import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import com.fincity.saas.notification.feign.IFeignCoreService;
import com.fincity.saas.notification.oserver.core.document.Connection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Getter
@Service
public class NotificationConnectionService {

    private static final String NOTIFICATION_CONN_CACHE = "notificationConn";
    private static final String NOTIFICATION_CONNECTION_TYPE = "NOTIFICATION";

    private IFeignCoreService coreService;

    private CacheService cacheService;

    @Autowired
    public void setCoreService(IFeignCoreService coreService) {
        this.coreService = coreService;
    }

    @Autowired
    public void setCacheService(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    public String getCacheName() {
        return NOTIFICATION_CONN_CACHE;
    }

    public Mono<Map<NotificationChannelType, Connection>> getNotificationConnections(
            String appCode, String clientCode, Map<NotificationChannelType, String> channelConnections) {

        if (channelConnections == null || channelConnections.isEmpty()) return Mono.empty();

        Map<NotificationChannelType, Connection> connections = new EnumMap<>(NotificationChannelType.class);

        return Flux.fromIterable(channelConnections.entrySet())
                .flatMap(connection -> this.getNotificationConn(appCode, clientCode, connection.getValue())
                        .filter(Objects::nonNull)
                        .doOnNext(connDetails -> connections.put(connection.getKey(), connDetails)))
                .then(Mono.just(connections));
    }

    public Mono<Connection> getNotificationConn(String appCode, String clientCode, String connectionName) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> coreService.getConnection(
                        clientCode, connectionName, appCode, clientCode, NOTIFICATION_CONNECTION_TYPE),
                appCode,
                clientCode,
                connectionName);
    }
}
