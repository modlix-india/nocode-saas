package com.fincity.saas.commons.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.github.benmanes.caffeine.cache.Caffeine;

class BoundedCaffeineCacheManagerTest {

    @Test
    void evictsLeastRecentlyUsedInstanceWhenOverCapacity() {

        BoundedCaffeineCacheManager manager = new BoundedCaffeineCacheManager(Caffeine.newBuilder(), 3,
                Duration.ofHours(1));

        manager.getCache("a");
        manager.getCache("b");
        manager.getCache("c");

        assertEquals(3, manager.getCacheNames().size());

        // touch "a" so it is not the least-recently-used entry
        manager.getCache("a");

        // adding a 4th distinct name must evict exactly one existing instance to stay at the cap
        manager.getCache("d");

        assertTrue(manager.getCacheNames().size() <= 3,
                "registry must never exceed the configured maximum instance count");
        assertTrue(manager.getCacheNames().contains("a"), "recently-touched name should survive eviction");
        assertTrue(manager.getCacheNames().contains("d"), "newly-created name must be present");
    }

    @Test
    void reusesTheSameCacheInstanceForRepeatedLookups() {

        BoundedCaffeineCacheManager manager = new BoundedCaffeineCacheManager(Caffeine.newBuilder(), 10,
                Duration.ofHours(1));

        org.springframework.cache.Cache first = manager.getCache("name");
        org.springframework.cache.Cache second = manager.getCache("name");

        assertNotNull(first);
        org.junit.jupiter.api.Assertions.assertSame(first, second);
    }

    @Test
    void underlyingWeightCapStillAppliesWithinEachInstance() {

        Caffeine<Object, Object> builder = Caffeine.newBuilder()
                .maximumWeight(10)
                .weigher((Object key, Object value) -> 5)
                .executor(Runnable::run);

        BoundedCaffeineCacheManager manager = new BoundedCaffeineCacheManager(builder, 10, Duration.ofHours(1));

        org.springframework.cache.Cache cache = manager.getCache("appCache");
        cache.put("k1", "v1");
        cache.put("k2", "v2");
        cache.put("k3", "v3");

        com.github.benmanes.caffeine.cache.Cache<?, ?> nativeCache = (com.github.benmanes.caffeine.cache.Cache<?, ?>) cache
                .getNativeCache();
        nativeCache.cleanUp();
        long size = nativeCache.estimatedSize();
        assertTrue(size <= 2, "per-instance weigher/maximumWeight must still bound content inside one cache");
    }
}
