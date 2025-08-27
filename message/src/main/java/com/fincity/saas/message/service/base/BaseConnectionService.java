package com.fincity.saas.message.service.base;

import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionType;
import com.fincity.saas.message.oserver.core.service.AbstractCoreService;
import reactor.core.publisher.Mono;

public abstract class BaseConnectionService extends AbstractCoreService<Connection> {

    private static final String CACHE_NAME_REST_OAUTH2 = "RestOAuthToken";

    protected String getObjectName() {
        return Connection.class.getSimpleName();
    }

    private String getCacheKey(String... entityNames) {
        return String.join(":", entityNames);
    }

    public abstract ConnectionType getConnectionType();

    @Override
    protected Mono<Connection> fetchCoreDocument(
            String appCode, String urlClientCode, String clientCode, String documentName) {
        return super.coreService.getConnection(
                urlClientCode,
                documentName,
                appCode,
                clientCode,
                getConnectionType().name());
    }

    public Mono<String> getConnectionOAuth2Token(String appCode, String clientCode, String connectionName) {
        return this.getCoreToken(appCode, clientCode, connectionName);
    }

    private Mono<String> getCoreToken(String appCode, String clientCode, String connectionName) {
        return super.cacheService.cacheValueOrGet(
                CACHE_NAME_REST_OAUTH2,
                () -> super.coreService.getConnectionOAuth2Token(clientCode, appCode, connectionName),
                this.getCacheKey(connectionName, clientCode, appCode));
    }
}
