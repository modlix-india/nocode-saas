package com.fincity.saas.commons.configuration;

import java.time.Duration;
import java.util.Collection;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * CacheManager whose name-to-Cache registry is itself a bounded Caffeine cache, so the number of
 * live per-name Caffeine cache instances cannot grow without limit. Spring's stock
 * {@link org.springframework.cache.caffeine.CaffeineCacheManager} auto-vivifies and permanently
 * retains a new Cache object for every distinct name ever requested via getCache(name), even on a
 * lookup miss - unbounded over time when callers build cache names from caller-supplied values
 * (e.g. one cache per (appCode, pageName)). Evicting a name here discards the whole Cache instance
 * (and its contents), reclaiming its memory; a subsequent lookup for that name starts a fresh cache.
 */
public class BoundedCaffeineCacheManager implements CacheManager {

    private final Caffeine<Object, Object> perCacheBuilder;
    private final com.github.benmanes.caffeine.cache.Cache<String, CaffeineCache> registry;

    public BoundedCaffeineCacheManager(Caffeine<Object, Object> perCacheBuilder, long maxInstances,
            Duration idleTimeout) {
        this.perCacheBuilder = perCacheBuilder;
        this.registry = Caffeine.newBuilder()
                .maximumSize(maxInstances)
                .expireAfterAccess(idleTimeout)
                // Synchronous eviction for the registry itself: it only ever holds lightweight
                // Cache wrapper objects (at most a few thousand), so the cost is negligible, and it
                // guarantees the live instance count never transiently exceeds maxInstances - the
                // property this class exists to provide.
                .executor(Runnable::run)
                .build();
    }

    @Override
    public Cache getCache(String name) {
        return this.registry.get(name, n -> new CaffeineCache(n, this.perCacheBuilder.build()));
    }

    @Override
    public Collection<String> getCacheNames() {
        return this.registry.asMap().keySet();
    }
}
