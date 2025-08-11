package com.fincity.saas.message.service.base;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.message.feign.IFeignCoreService;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionType;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class BaseConnectionService {

    private static final String CACHE_NAME_REST_OAUTH2 = "RestOAuthToken";
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

    private String getCacheKey(String... entityNames) {
        return String.join(":", entityNames);
    }

    public abstract ConnectionType getConnectionType();

    public Mono<Connection> getConnection(String appCode, String clientCode, String connectionName) {
        return FlatMapUtil.flatMapMono(
                () -> this.securityService.appInheritance(appCode, clientCode, clientCode),
                inheritance ->
                        this.getMessageConn(appCode, connectionName, getConnectionType(), clientCode, inheritance));
    }

    public Mono<String> getConnectionOAuth2Token(String appCode, String clientCode, String connectionName) {
        return this.getCoreToken(appCode, clientCode, connectionName);
    }

    private Mono<Connection> getMessageConn(
            String appCode,
            String connectionName,
            ConnectionType connectionType,
            String clientCode,
            List<String> inheritance) {

        if (inheritance == null || inheritance.isEmpty()) return Mono.empty();

        if (inheritance.size() == 1)
            return this.cacheService
                    .get(this.getCacheNames(appCode, connectionName), inheritance.getFirst())
                    .cast(Connection.class)
                    .switchIfEmpty(this.getCoreConn(appCode, connectionName, connectionType.name(), clientCode));

        return Flux.fromIterable(inheritance)
                .flatMap(cc -> this.cacheService.get(this.getCacheNames(appCode, connectionName), cc))
                .cast(Connection.class)
                .next()
                .switchIfEmpty(this.getCoreConn(appCode, connectionName, connectionType.name(), clientCode));
    }

    private Mono<Connection> getCoreConn(
            String appCode, String connectionName, String connectionType, String clientCode) {
        return FlatMapUtil.flatMapMono(
                () -> coreService.getConnection(clientCode, connectionName, appCode, clientCode, connectionType),
                connection -> this.cacheService.put(
                        this.getCacheNames(appCode, connectionName), connection, connection.getClientCode()));
    }

    private Mono<String> getCoreToken(String appCode, String clientCode, String connectionName) {
        return this.cacheService.cacheValueOrGet(
                CACHE_NAME_REST_OAUTH2,
                () -> this.coreService.getConnectionOAuth2Token("", "", clientCode, appCode, connectionName),
                this.getCacheKey(connectionName, clientCode, appCode));
    }
}
