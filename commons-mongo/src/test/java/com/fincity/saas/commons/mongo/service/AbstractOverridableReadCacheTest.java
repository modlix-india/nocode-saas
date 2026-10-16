package com.fincity.saas.commons.mongo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mongo.document.AbstractSchema;
import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;
import com.fincity.saas.commons.mongo.repository.InheritanceService;
import com.fincity.saas.commons.service.CacheService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A cache hit in readInternal(name, appCode, ...) used to read the document and its
 * ancestors from Mongo again and then throw that read away, serving the cached copy
 * anyway. So the cache saved no database reads at all: the event scheduler's
 * per-client readEffective calls alone made 11k eventDefinition finds a minute on
 * dev. A hit now touches no repository method.
 */
class AbstractOverridableReadCacheTest {

    private static final String APP = "appbuilder";
    private static final String NAME = "nightly";

    static class TestSchema extends AbstractSchema<TestSchema> {
        private static final long serialVersionUID = 1L;

        public TestSchema() {
            super();
        }

        public TestSchema(TestSchema other) {
            super(other);
        }
    }

    /** One instance's in-process cache: what Caffeine holds, without Redis. */
    static class MapCache extends CacheService {

        final Map<String, Object> values = new HashMap<>();

        @SuppressWarnings("unchecked")
        @Override
        public <T> Mono<T> get(String cName, Object... keys) {
            return Mono.justOrEmpty((T) this.values.get(cName + ":" + keys[0]));
        }

        @Override
        public <T> Mono<T> put(String cName, T value, Object... keys) {
            this.values.put(cName + ":" + keys[0], value);
            return Mono.just(value);
        }
    }

    /** The stored documents, and every repository method the service called. */
    static class Store {

        final Map<String, TestSchema> byId = new LinkedHashMap<>();
        final List<String> calls = new ArrayList<>();

        TestSchema add(String id, String clientCode, String baseClientCode, Map<String, Object> definition) {
            TestSchema s = new TestSchema();
            s.setId(id);
            s.setName(NAME);
            s.setAppCode(APP);
            s.setClientCode(clientCode);
            s.setBaseClientCode(baseClientCode);
            s.setDefinition(new HashMap<>(definition));
            this.byId.put(id, s);
            return s;
        }

        // Copies, because applyOverride merges into the document it is handed.
        @SuppressWarnings("unchecked")
        IOverridableDataRepository<TestSchema> repo() {
            return (IOverridableDataRepository<TestSchema>) Proxy.newProxyInstance(
                    IOverridableDataRepository.class.getClassLoader(),
                    new Class<?>[] { IOverridableDataRepository.class }, (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class)
                            return method.getName().equals("equals") ? proxy == args[0]
                                    : method.getName().equals("hashCode") ? System.identityHashCode(proxy)
                                            : "Store.repo";

                        this.calls.add(method.getName());
                        return switch (method.getName()) {
                            case "findById" -> Mono.justOrEmpty(this.byId.get((String) args[0]))
                                    .map(TestSchema::new);
                            case "findOneByNameAndAppCodeAndClientCode" -> Flux.fromIterable(this.byId.values())
                                    .filter(s -> s.getName().equals(args[0]) && s.getAppCode().equals(args[1])
                                            && s.getClientCode().equals(args[2]))
                                    .next()
                                    .map(TestSchema::new);
                            case "findByNameAndAppCodeAndClientCodeIn" -> Flux.fromIterable(this.byId.values())
                                    .filter(s -> s.getName().equals(args[0]) && s.getAppCode().equals(args[1])
                                            && ((List<?>) args[2]).contains(s.getClientCode()))
                                    .map(TestSchema::new);
                            default -> throw new UnsupportedOperationException(method.getName());
                        };
                    });
        }
    }

    static class TestService extends AbstractOverridableDataService<TestSchema, IOverridableDataRepository<TestSchema>> {

        TestService(Store store, CacheService cache, List<String> chain) {
            super(TestSchema.class);
            this.repo = store.repo();
            this.cacheService = cache;
            this.inheritanceService = new InheritanceService() {

                @Override
                public Mono<List<String>> order(String appCode, String urlClientCode, String clientCode) {
                    return Mono.just(chain);
                }
            };
        }

        @Override
        protected Mono<TestSchema> updatableEntity(TestSchema entity) {
            return Mono.just(entity);
        }
    }

    private static TestSchema read(TestService service, String clientCode, boolean draft) {
        ObjectWithUniqueID<TestSchema> read = service.readInternal(NAME, APP, clientCode, clientCode, draft).block();
        return read == null ? null : read.getObject();
    }

    @Test
    void aCacheHitReadsNothingFromTheDatabase() {

        Store store = new Store();
        store.add("s1", "SYSTEM", null, Map.of("cron", "0 0 * * * *"));
        TestService service = new TestService(store, new MapCache(), List.of("SYSTEM"));

        TestSchema first = read(service, "SYSTEM", false);
        assertTrue(!store.calls.isEmpty(), "the first read goes to the database");

        store.calls.clear();
        TestSchema second = read(service, "SYSTEM", false);

        assertEquals(List.of(), store.calls);
        assertEquals(first.getDefinition(), second.getDefinition());
        assertEquals(first.getClientCode(), second.getClientCode());
    }

    @Test
    void aMergedOverrideIsServedFromTheCacheWithoutReadingItsAncestors() {

        Store store = new Store();
        store.add("s1", "SYSTEM", null, Map.of("cron", "0 0 * * * *", "timezone", "UTC"));
        store.add("f1", "FIN", "SYSTEM", Map.of("timezone", "Asia/Kolkata"));
        TestService service = new TestService(store, new MapCache(), List.of("SYSTEM", "FIN"));

        TestSchema first = read(service, "FIN", false);
        assertTrue(store.calls.contains("findOneByNameAndAppCodeAndClientCode"),
                "the first read walks to the ancestor: " + store.calls);

        store.calls.clear();
        TestSchema second = read(service, "FIN", false);

        assertEquals(List.of(), store.calls);
        assertEquals(first.getDefinition(), second.getDefinition());
    }

    @Test
    void anotherClientIsStillAMiss() {

        Store store = new Store();
        store.add("s1", "SYSTEM", null, Map.of("cron", "0 0 * * * *"));
        TestService service = new TestService(store, new MapCache(), List.of("SYSTEM"));

        read(service, "FIN", false);
        store.calls.clear();
        read(service, "AKASH", false);

        assertTrue(store.calls.contains("findById"), "a different client is a different key: " + store.calls);
    }

    @Test
    void liveAndDraftDoNotShareAnEntry() {

        Store store = new Store();
        store.add("s1", "SYSTEM", null, Map.of("cron", "0 0 * * * *"));
        TestService service = new TestService(store, new MapCache(), List.of("SYSTEM"));

        read(service, "SYSTEM", false);
        store.calls.clear();
        read(service, "SYSTEM", true);

        assertTrue(store.calls.contains("findById"), "the draft surface has its own cache: " + store.calls);
    }
}
