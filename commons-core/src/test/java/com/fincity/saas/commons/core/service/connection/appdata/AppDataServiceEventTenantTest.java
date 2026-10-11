package com.fincity.saas.commons.core.service.connection.appdata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.document.EventDefinition;
import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.enums.ConnectionSubType;
import com.fincity.saas.commons.core.enums.ConnectionType;
import com.fincity.saas.commons.core.service.ConnectionService;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.core.service.EventDefinitionService;
import com.fincity.saas.commons.core.service.StorageService;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Which app and client a storage event is raised for.
 *
 * A KIRun Storage.Delete normally leaves appCode and clientCode blank, and AppDataService resolves
 * them (the function's owner app or the request's app, and the caller's client). delete used to
 * hand the RAW blanks to generateEvent, so the EventDefinition was looked up under no app and the
 * event went out with no app or client. The event must carry the resolved tenant.
 */
class AppDataServiceEventTenantTest {

    /** What the stubs saw: EventDefinition lookups and raised events, as "app/client/event". */
    private final List<String> lookups = new ArrayList<>();
    private final List<String> raised = new ArrayList<>();

    private AppDataService service(Storage storage) throws Exception {

        AppDataService service = new AppDataService() {
            // delete reads the row first on a generateEvents storage, to put it in the event.
            @Override
            public Mono<Map<String, Object>> read(String appCode, String clientCode, String storageName, String id,
                    Boolean eager, List<String> eagerFields) {
                return Mono.just(Map.of("_id", id, "name", "row " + id));
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

            @Override
            public Mono<List<String>> storagesReferencing(String appCode, String storageName) {
                return Mono.just(List.of());
            }
        };

        EventDefinitionService definitions = new EventDefinitionService() {
            @Override
            public Mono<ObjectWithUniqueID<EventDefinition>> read(String name, String appCode, String clientCode) {
                lookups.add(appCode + "/" + clientCode + "/" + name);
                return Mono.just(new ObjectWithUniqueID<>(new EventDefinition()));
            }
        };

        EventCreationService events = new EventCreationService() {
            @Override
            public Mono<Boolean> createEvent(EventQueObject queObj) {
                raised.add(queObj.getAppCode() + "/" + queObj.getClientCode() + "/" + queObj.getEventName());
                return Mono.just(true);
            }
        };

        IAppDataService backend = (IAppDataService) Proxy.newProxyInstance(
                IAppDataService.class.getClassLoader(), new Class<?>[] { IAppDataService.class },
                (proxy, method, args) -> {
                    if ("delete".equals(method.getName()))
                        return Mono.just(Boolean.TRUE);
                    throw new UnsupportedOperationException(method.getName());
                });

        set(service, "connectionService", connections);
        set(service, "storageService", storages);
        set(service, "eventDefinitionService", definitions);
        set(service, "ecService", events);
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

    private static Storage eventStorage() {
        Storage storage = new Storage();
        storage.setName("whatsappOutbox");
        storage.setAppCode("leadzumptwo");
        storage.setClientCode("SYSTEM");
        storage.setGenerateEvents(true);
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

    @Test
    void aDeleteWithBlankAppAndClientRaisesItsEventForTheResolvedTenant() throws Exception {

        StepVerifier.create(this.service(eventStorage())
                .delete("", "", "whatsappOutbox", "42", false)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(caller())))
                .expectNext(Boolean.TRUE)
                .verifyComplete();

        assertEquals(List.of("leadzumptwo/FIN/Storage.whatsappOutbox.Delete"), this.lookups);
        assertEquals(List.of("leadzumptwo/FIN/Storage.whatsappOutbox.Delete"), this.raised);
    }

    @Test
    void aDeleteWithNullAppAndClientRaisesItsEventForTheResolvedTenant() throws Exception {

        StepVerifier.create(this.service(eventStorage())
                .delete(null, null, "whatsappOutbox", "42", false)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(caller())))
                .expectNext(Boolean.TRUE)
                .verifyComplete();

        assertEquals(List.of("leadzumptwo/FIN/Storage.whatsappOutbox.Delete"), this.raised);
    }

    @Test
    void anExplicitAppAndClientAreKept() throws Exception {

        StepVerifier.create(this.service(eventStorage())
                .delete("leadzumptwo", "FIN", "whatsappOutbox", "42", false)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(caller())))
                .expectNext(Boolean.TRUE)
                .verifyComplete();

        assertEquals(List.of("leadzumptwo/FIN/Storage.whatsappOutbox.Delete"), this.raised);
    }
}
