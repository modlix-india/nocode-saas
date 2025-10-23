package com.fincity.saas.entity.collector.service.commons;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.entity.collector.fiegn.IFeignCoreService;
import com.fincity.saas.entity.collector.service.EntityCollectorMessageResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

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
        return this.cacheService.cacheValueOrGet(
                CACHE_NAME_REST_OAUTH2,
                () -> coreService.getConnectionOAuth2Token( clientCode, appCode, connectionName ),
                this.getCacheKey(connectionName, clientCode, appCode));
    }

    private String getCacheKey(String... entityNames) {
        return String.join(":", entityNames);
    }
}
