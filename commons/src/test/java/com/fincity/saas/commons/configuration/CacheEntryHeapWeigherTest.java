package com.fincity.saas.commons.configuration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;

/**
 * The cache weigher must approximate RETAINED HEAP, not serialised length.
 *
 * The bug these cover: weighing an entry by its serialised JSON bytes made a
 * deeply-nested definition look ~30x cheaper than it is, so the configured
 * maximumWeight could never fire and the ui service OOM'd with a "bounded"
 * cache reporting 29 MiB while retaining ~930 MB.
 */
class CacheEntryHeapWeigherTest {

    /** Concrete handle on the abstract configuration so the weigher bean can be built. */
    private static final class TestConfiguration extends AbstractBaseConfiguration {
        private TestConfiguration() {
            super(new ObjectMapper());
        }
    }

    private static Cache<Object, Object> weighingCache() {

        TestConfiguration configuration = new TestConfiguration();

        // The @Value fields are Spring-injected in production; without a context they
        // default to 0, which would cap the cache at zero weight and evict everything.
        setField(configuration, "localCacheMaxWeightBytes", Long.MAX_VALUE);
        setField(configuration, "localCacheExpireAfterWriteMinutes", 0L);

        return configuration.caffeineConfig().executor(Runnable::run).build();
    }

    private static void setField(Object target, String name, long value) {
        try {
            java.lang.reflect.Field field = AbstractBaseConfiguration.class.getDeclaredField(name);
            field.setAccessible(true);
            field.setLong(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not prime " + name, e);
        }
    }

    private static long weightOf(Object value) {
        Cache<Object, Object> cache = weighingCache();
        cache.put("k", value);
        cache.cleanUp();
        return cache.policy().eviction().orElseThrow().weightedSize().orElseThrow();
    }

    /** A tree of small keys costs far more than its JSON text length. */
    @Test
    void nestedMapWeighsFarMoreThanItsSerialisedLength() throws Exception {

        Map<String, Object> leaf = new LinkedHashMap<>();
        leaf.put("a", "1");
        leaf.put("b", "2");
        leaf.put("c", "3");

        Map<String, Object> root = new LinkedHashMap<>();
        for (int i = 0; i < 50; i++)
            root.put("key" + i, new LinkedHashMap<>(leaf));

        int serialisedLength = new ObjectMapper().writeValueAsString(root).length();
        long weight = weightOf(root);

        assertTrue(weight > serialisedLength * 5L,
                "a nested map of small keys must weigh several times its serialised length, but "
                        + weight + " was not far above " + serialisedLength);
    }

    /** One long string is genuinely about as big as its text, so it must not be inflated. */
    @Test
    void longStringIsNotInflated() {

        String big = "x".repeat(20000);

        long weight = weightOf(Map.of("text", big));

        assertTrue(weight < big.length() * 2L,
                "a single long string should weigh about its length, not a multiple of it: " + weight);
        assertTrue(weight > big.length(), "the string's characters must still be counted: " + weight);
    }

    /** Shape, not just length, drives the weight: same text size, very different heap cost. */
    @Test
    void shapeDominatesWeightNotTextLength() throws Exception {

        ObjectMapper mapper = new ObjectMapper();

        Map<String, Object> many = new LinkedHashMap<>();
        for (int i = 0; i < 400; i++)
            many.put("k" + i, i);

        List<Object> few = new ArrayList<>();
        few.add(mapper.writeValueAsString(many));

        // Comparable serialised size, but `many` is 400 map entries and `few` is one string.
        assertTrue(weightOf(many) > weightOf(few) * 3L,
                "400 small entries must outweigh the same bytes held as a single string");
    }

    /** An entry that cannot be serialised must still be charged, never treated as free. */
    @Test
    void unserialisableValueIsStillCharged() {

        Object unserialisable = new Object() {
            @SuppressWarnings("unused")
            public String getBoom() {
                throw new IllegalStateException("not serialisable");
            }
        };

        assertTrue(weightOf(unserialisable) > 0, "an unweighable entry must not be free");
    }
}
