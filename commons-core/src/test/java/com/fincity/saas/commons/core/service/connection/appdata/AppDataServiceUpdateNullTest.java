package com.fincity.saas.commons.core.service.connection.appdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.enums.ConnectionSubType;
import com.fincity.saas.commons.core.enums.ConnectionType;
import com.fincity.saas.commons.core.enums.StorageRelationType;
import com.fincity.saas.commons.core.model.DataObject;
import com.fincity.saas.commons.core.model.StorageRelation;
import com.fincity.saas.commons.core.service.ConnectionService;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.core.service.StorageService;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * A field sent as null must reach the backend as null.
 *
 * For any storage with a relation, AppDataService copied the row with
 * CloneUtil.cloneMapObject, which drops null values, and handed the copy on in place
 * of the caller's data. So PUT and PATCH with {@code description: null} (or a
 * relation set to null) returned 200 and the backend never heard about the field: the
 * old value stayed, on MySQL and on a Mongo PATCH alike.
 */
class AppDataServiceUpdateNullTest {

    /** What the backend's update was handed, and with which override flag. */
    private final List<Map<String, Object>> updated = new ArrayList<>();
    private final List<Object> overrides = new ArrayList<>();

    private AppDataService service(Storage storage) throws Exception {

        AppDataService service = new AppDataService() {
            // processRelationsForUpdate reads the existing row first.
            @Override
            public Mono<Map<String, Object>> read(String appCode, String clientCode, String storageName, String id,
                    Boolean eager, List<String> eagerFields) {
                return Mono.just(Map.of("_id", id, "description", "old", "ownerId", "9003"));
            }
        };

        ConnectionService connections = new ConnectionService() {
            @Override
            public Mono<Connection> read(String name, String appCode, String clientCode, ConnectionType type) {
                return Mono.empty();
            }
        };

        StorageService storages = new StorageService(null, null, null, null) {
            @Override
            public Mono<ObjectWithUniqueID<Storage>> read(String name, String appCode, String clientCode) {
                return Mono.just(new ObjectWithUniqueID<>(storage));
            }
        };

        IAppDataService backend = (IAppDataService) Proxy.newProxyInstance(
                IAppDataService.class.getClassLoader(), new Class<?>[] { IAppDataService.class },
                (proxy, method, args) -> {
                    if ("update".equals(method.getName())) {
                        DataObject dob = (DataObject) args[3];
                        updated.add(new LinkedHashMap<>(dob.getData()));
                        overrides.add(args[4]);
                        return Mono.just(dob.getData());
                    }
                    if ("checkIfExists".equals(method.getName()))
                        return Mono.just(Boolean.TRUE);
                    throw new UnsupportedOperationException(method.getName());
                });

        set(service, "connectionService", connections);
        set(service, "storageService", storages);
        set(service, "msgService", new CoreMessageResourceService());
        set(service, "securityService", Proxy.newProxyInstance(
                IFeignSecurityService.class.getClassLoader(), new Class<?>[] { IFeignSecurityService.class },
                (proxy, method, args) -> {
                    if ("doesClientManageClientCode".equals(method.getName()))
                        return Mono.just(args[0].equals(args[1]));
                    throw new UnsupportedOperationException(method.getName());
                }));

        @SuppressWarnings("unchecked")
        Map<ConnectionSubType, IAppDataService> services = (Map<ConnectionSubType, IAppDataService>) get(service,
                "services");
        services.put(ConnectionSubType.MONGO, backend);
        services.put(ConnectionSubType.MYSQL, backend);

        return service;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field f = AppDataService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Object get(Object target, String name) throws Exception {
        Field f = AppDataService.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static Storage projectWithRelation() {
        StorageRelation owner = new StorageRelation();
        owner.setStorageName("owner");
        owner.setRelationType(StorageRelationType.TO_ONE);

        Storage storage = new Storage();
        storage.setName("project");
        storage.setAppCode("leadzumptwo");
        storage.setClientCode("SYSTEM");
        storage.setRelations(Map.of("ownerId", owner));
        return storage;
    }

    private static ContextAuthentication caller() {
        ContextAuthentication ca = new ContextAuthentication()
                .setUser(new ContextUser().setId(BigInteger.valueOf(110)).setClientId(BigInteger.valueOf(28))
                        .setStringAuthorities(List.of("Authorities.Logged_IN")))
                .setClientCode("FIN")
                .setClientLevelType("CLIENT")
                .setUrlClientCode("FIN")
                .setUrlAppCode("leadzumptwo");
        ca.setAuthenticated(true);
        return ca;
    }

    private static DataObject row(Map<String, Object> data) {
        return new DataObject().setData(new LinkedHashMap<>(data));
    }

    private void update(DataObject dob, boolean override) throws Exception {
        StepVerifier.create(this.service(projectWithRelation())
                        .update("leadzumptwo", "FIN", "project", dob, override, false, List.of())
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(caller())))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void aPatchWithAPlainFieldSetToNullHandsTheNullOn() throws Exception {

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("_id", "426");
        data.put("description", null);
        this.update(row(data), false);

        assertEquals(1, this.updated.size());
        assertTrue(this.updated.getFirst().containsKey("description"), "the null was dropped: " + this.updated);
        assertNull(this.updated.getFirst().get("description"));
        assertEquals(Boolean.FALSE, this.overrides.getFirst());
    }

    @Test
    void aPutWithARelationSetToNullHandsTheNullOn() throws Exception {

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("_id", "426");
        data.put("name", "Project");
        data.put("ownerId", null);
        this.update(row(data), true);

        assertTrue(this.updated.getFirst().containsKey("ownerId"), "the null relation was dropped: " + this.updated);
        assertNull(this.updated.getFirst().get("ownerId"));
        assertEquals("Project", this.updated.getFirst().get("name"));
        assertEquals(Boolean.TRUE, this.overrides.getFirst());
    }

    @Test
    void aNonNullRelationIsStillCheckedAndKept() throws Exception {

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("_id", "426");
        data.put("ownerId", "9003");
        data.put("description", null);
        this.update(row(data), false);

        assertEquals("9003", this.updated.getFirst().get("ownerId"));
        assertTrue(this.updated.getFirst().containsKey("description"));
    }

    @Test
    void theCopyKeepsNulls() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("a", null);
        data.put("b", 1);
        Map<String, Object> copy = AppDataService.copyKeepingNulls(data);
        assertEquals(data, copy);
        assertTrue(copy.containsKey("a"));
        assertTrue(AppDataService.copyKeepingNulls(null).isEmpty());
    }
}
