package com.fincity.saas.notification.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import com.fincity.saas.notification.feign.IFeignCoreService;
import com.fincity.saas.notification.oserver.core.document.Connection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fincity.saas.notification.oserver.core.enums.ConnectionType;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Getter
@Service
public class NotificationConnectionService {

    private static final String NOTIFICATION_CONNECTION_TYPE = ConnectionType.NOTIFICATION.name();

    private CacheService cacheService;

    private IFeignCoreService coreService;

    private IFeignSecurityService securityService;

    @Autowired
    private void setCacheService(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Autowired
    private void setCoreService(IFeignCoreService coreService) {
        this.coreService = coreService;
    }

    @Autowired
    private void setSecurityService(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    private String getCacheNames(String... entityNames) {
        return String.join("_", entityNames);
    }

    public Mono<Map<NotificationChannelType, Connection>> getNotificationConnections(
            String appCode, String clientCode, Map<NotificationChannelType, String> channelConnections) {

        if (channelConnections == null || channelConnections.isEmpty()) return Mono.empty();

        Map<NotificationChannelType, Connection> connections = new EnumMap<>(NotificationChannelType.class);

        return FlatMapUtil.flatMapMono(
                () -> this.securityService.appInheritance(appCode, clientCode, clientCode),
                inheritance -> Flux.fromIterable(channelConnections.entrySet())
                        .flatMap(connection -> this.getNotificationConn(
                                        appCode, connection.getValue(), clientCode, inheritance)
                                .filter(Objects::nonNull)
                                .doOnNext(connDetails -> connections.put(connection.getKey(), connDetails)))
                        .then(Mono.just(connections)));
    }

    public Mono<Connection> getNotificationConnection(String appCode, String connectionName, String clientCode) {
        return FlatMapUtil.flatMapMono(
                () -> this.securityService.appInheritance(appCode, clientCode, clientCode),
                inheritance -> this.getNotificationConn(appCode, connectionName, clientCode, inheritance));
    }

    private Mono<Connection> getNotificationConn(
            String appCode, String connectionName, String clientCode, List<String> inheritance) {

        if (inheritance == null || inheritance.isEmpty()) return Mono.empty();

        if (inheritance.size() == 1)
            return this.cacheService
                    .get(this.getCacheNames(appCode, connectionName), inheritance.getFirst())
                    .cast(Connection.class)
                    .switchIfEmpty(this.getCoreNotificationConn(appCode, connectionName, clientCode));

        return Flux.fromIterable(inheritance)
                .flatMap(cc -> this.cacheService.get(this.getCacheNames(appCode, connectionName), cc))
                .cast(Connection.class)
                .next()
                .switchIfEmpty(this.getCoreNotificationConn(appCode, connectionName, clientCode));
    }

    private Mono<Connection> getCoreNotificationConn(String appCode, String connectionName, String clientCode) {
        return FlatMapUtil.flatMapMono(
                () -> coreService.getConnection(
                        clientCode, connectionName, appCode, clientCode, NOTIFICATION_CONNECTION_TYPE),
                connection -> this.cacheService.put(
                        this.getCacheNames(appCode, connectionName), connection, connection.getClientCode()));
    }
}
