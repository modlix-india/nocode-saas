package com.fincity.saas.message.service;

import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.message.enums.channel.MessageChannelType;
import com.fincity.saas.message.feign.IFeignCoreService;
import com.fincity.saas.message.oserver.core.document.Connection;
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
public class MessageConnectionService {

    private static final String MESSAGE_CONN_CACHE = "messageConn";
    private static final String MESSAGE_CONNECTION_TYPE = "MESSAGE";

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
        return MESSAGE_CONN_CACHE;
    }

    public Mono<Map<MessageChannelType, Connection>> getMessageConnections(
            String appCode, String clientCode, Map<MessageChannelType, String> channelConnections) {

        if (channelConnections == null || channelConnections.isEmpty()) return Mono.empty();

        Map<MessageChannelType, Connection> connections = new EnumMap<>(MessageChannelType.class);

        return Flux.fromIterable(channelConnections.entrySet())
                .flatMap(connection -> this.getMessageConn(appCode, clientCode, connection.getValue())
                        .filter(Objects::nonNull)
                        .doOnNext(connDetails -> connections.put(connection.getKey(), connDetails)))
                .then(Mono.just(connections));
    }

    public Mono<Connection> getMessageConn(String appCode, String clientCode, String connectionName) {
        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> coreService.getConnection(
                        clientCode, connectionName, appCode, clientCode, MESSAGE_CONNECTION_TYPE),
                appCode,
                clientCode,
                connectionName);
    }
}
