package com.fincity.saas.ui.service;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;

/**
 * Service to evict SSR (Server-Side Rendering) cache when UI-related entities are modified.
 * Uses Redis Pub/Sub to broadcast cache invalidation messages to all SSR instances.
 */
@Service
public class SSRCacheEvictionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SSRCacheEvictionService.class);
    private static final String SSR_CACHE_INVALIDATION_CHANNEL = "ssr:cache:invalidation";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public SSRCacheEvictionService(ReactiveStringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initialize() {
        LOGGER.info("SSR cache eviction service initialized with Redis Pub/Sub channel: {}", SSR_CACHE_INVALIDATION_CHANNEL);
    }

    /**
     * Evict all SSR cache entries for a specific app.
     *
     * @param appCode The application code
     * @return Mono<Boolean> indicating success
     */
    public Mono<Boolean> evictByAppCode(String appCode) {
        return this.publishInvalidation(appCode, null, null);
    }

    /**
     * Evict SSR cache entries for a specific app and client.
     *
     * @param appCode    The application code
     * @param clientCode The client code
     * @return Mono<Boolean> indicating success
     */
    public Mono<Boolean> evictByAppAndClient(String appCode, String clientCode) {
        return this.publishInvalidation(appCode, clientCode, null);
    }

    /**
     * Evict SSR cache entries for a specific page.
     *
     * @param appCode    The application code
     * @param clientCode The client code
     * @param pageName   The page name
     * @return Mono<Boolean> indicating success
     */
    public Mono<Boolean> evictByPage(String appCode, String clientCode, String pageName) {
        return this.publishInvalidation(appCode, clientCode, pageName);
    }

    /**
     * Publish cache invalidation message to Redis Pub/Sub channel.
     * All SSR instances subscribed to this channel will receive and process the message.
     */
    private Mono<Boolean> publishInvalidation(String appCode, String clientCode, String pageName) {
        if (appCode == null || appCode.isBlank()) {
            LOGGER.warn("SSR cache eviction skipped: appCode is required");
            return Mono.just(false);
        }

        Map<String, Object> message = new HashMap<>();
        message.put("appCode", appCode);
        if (clientCode != null && !clientCode.isBlank()) {
            message.put("clientCode", clientCode);
        }
        if (pageName != null && !pageName.isBlank()) {
            message.put("pageName", pageName);
        }
        message.put("timestamp", System.currentTimeMillis());

        String messageJson;
        try {
            messageJson = this.objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to serialize SSR cache invalidation message", e);
            return Mono.just(false);
        }

        return this.redisTemplate.convertAndSend(SSR_CACHE_INVALIDATION_CHANNEL, messageJson)
                .map(subscriberCount -> {
                    LOGGER.debug("SSR cache invalidation published: appCode={}, clientCode={}, pageName={}, subscribers={}",
                            appCode, clientCode, pageName, subscriberCount);
                    return true;
                })
                .onErrorResume(ex -> {
                    LOGGER.warn("SSR cache eviction failed for appCode={}: {}", appCode, ex.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Creates a function that can be used in flatMap chains to evict SSR cache by app code.
     *
     * @param appCode The application code
     * @return A function that evicts the cache and returns the input value
     */
    public <T> java.util.function.Function<T, Mono<T>> evictByAppCodeFunction(String appCode) {
        return value -> this.evictByAppCode(appCode).thenReturn(value);
    }

    /**
     * Creates a function that can be used in flatMap chains to evict SSR cache.
     *
     * @param appCode    The application code
     * @param clientCode The client code (optional)
     * @return A function that evicts the cache and returns the input value
     */
    public <T> java.util.function.Function<T, Mono<T>> evictFunction(String appCode, String clientCode) {
        return value -> this.evictByAppAndClient(appCode, clientCode).thenReturn(value);
    }
}
