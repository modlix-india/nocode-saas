package com.fincity.saas.entity.processor.service.commons;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.entity.processor.feign.IFeignCoreService;
import com.fincity.saas.entity.processor.oserver.core.enums.ConnectionType;
import com.fincity.saas.entity.processor.service.EntityCollectorMessageResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

public abstract class AbstractConnectionService {

    private static final String CACHE_NAME_REST_OAUTH2 = "RestOAuthToken";
    private static final String CACHE_NAME_REST_CONNECTION_DETAIL = "RestConnectionDetail";

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

    /**
     * Fetches one key from a Connection record's {@code connectionDetails} map
     * (per-tenant Connection config in Core). Used for per-client secrets like
     * the Google Ads developer token or Meta dataset id without baking them into
     * code. Returns {@code Mono.empty()} when the key is absent — callers should
     * provide a sensible fallback or throw.
     *
     * <p>Caches the full {@code connectionDetails} map under
     * {@code RestConnectionDetail} keyed by ({@code appCode}, {@code clientCode},
     * {@code connectionName}) for the same TTL as the OAuth token cache.
     */
    public Mono<String> getConnectionDetail(
            String appCode, String clientCode, String connectionName, String key) {

        String cacheKey = this.getCacheKey(connectionName, clientCode, appCode);

        return this.cacheService
                .<Map<String, Object>>get(CACHE_NAME_REST_CONNECTION_DETAIL, cacheKey)
                .switchIfEmpty(this.fetchAndCacheConnectionDetails(appCode, clientCode, connectionName, cacheKey))
                .flatMap(map -> Mono.justOrEmpty(map.get(key)))
                .map(Object::toString);
    }

    private Mono<Map<String, Object>> fetchAndCacheConnectionDetails(
            String appCode, String clientCode, String connectionName, String cacheKey) {

        return this.coreService
                .getConnection(connectionName, appCode, clientCode, clientCode, ConnectionType.REST_API.name())
                .flatMap(connection -> {
                    Map<String, Object> details = connection.getConnectionDetails();
                    if (details == null) details = Map.of();
                    return this.cacheService
                            .put(CACHE_NAME_REST_CONNECTION_DETAIL, details, cacheKey)
                            .thenReturn(details);
                });
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
