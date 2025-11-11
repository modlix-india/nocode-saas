package com.fincity.saas.entity.collector.service.commons;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.entity.collector.fiegn.IFeignCoreService;
import com.fincity.saas.entity.collector.service.EntityCollectorMessageResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

public abstract class AbstractConnectionService {

    private static final String CACHE_NAME_REST_OAUTH2 = "RestOAuthToken";

    protected EntityCollectorMessageResourceService msgService;
    protected CacheService cacheService;
    protected IFeignCoreService coreService;

    @Autowired
    private void setMsgService(EntityCollectorMessageResourceService msgService) {
        this.msgService = msgService;
    }

    @Autowired
    private void setCacheService(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Autowired
    private void setCoreService(IFeignCoreService coreService) {
        this.coreService = coreService;
    }

    public Mono<String> getConnectionOAuth2Token(String appCode, String clientCode, String connectionName) {
        return this.getCoreToken(appCode, clientCode, connectionName)
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        EntityCollectorMessageResourceService.TOKEN_UNAVAILABLE,
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

    private String getCacheKey(String... entityNames) {
        return String.join(":", entityNames);
    }
}
