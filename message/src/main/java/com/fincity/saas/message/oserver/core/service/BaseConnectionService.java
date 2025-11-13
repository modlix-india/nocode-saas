package com.fincity.saas.message.oserver.core.service;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionType;
import com.fincity.saas.message.service.MessageResourceService;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
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
        return super.coreService
                .getConnection(
                        documentName,
                        appCode,
                        clientCode,
                        urlClientCode,
                        getConnectionType().name())
                .switchIfEmpty(super.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        MessageResourceService.CONNECTION_NOT_FOUND,
                        documentName));
    }

    public Mono<String> getConnectionOAuth2Token(String appCode, String clientCode, String connectionName) {
        return this.getCoreToken(appCode, clientCode, connectionName)
                .switchIfEmpty(super.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        MessageResourceService.CONNECTION_TOKEN_NOT_FOUND,
                        connectionName));
    }

    private Mono<String> getCoreToken(String appCode, String clientCode, String connectionName) {

        return this.cacheService
                .<Map<String, Object>>get(CACHE_NAME_REST_OAUTH2, this.getCacheKey(connectionName, clientCode, appCode))
                .flatMap(coreToken -> {
                    Object expObj = coreToken.get("expiresAt");
                    LocalDateTime expiresAt = (expObj instanceof LocalDateTime) ? (LocalDateTime) expObj : null;
                    boolean valid = (expiresAt == null) || expiresAt.isAfter(LocalDateTime.now());

                    if (valid) {
                        Object tokenObj = coreToken.get("token");
                        return Mono.justOrEmpty(tokenObj).map(Object::toString);
                    }

                    return this.coreService.getConnectionOAuth2Token(clientCode, appCode, connectionName);
                })
                .switchIfEmpty(this.coreService.getConnectionOAuth2Token(clientCode, appCode, connectionName));
    }
}
