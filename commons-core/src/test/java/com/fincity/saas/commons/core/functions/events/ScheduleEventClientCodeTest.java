package com.fincity.saas.commons.core.functions.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import com.fincity.nocode.kirun.engine.function.reactive.AbstractReactiveFunction;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.saas.commons.core.document.EventDefinition;
import com.fincity.saas.commons.core.scheduler.ScheduledEvent;
import com.fincity.saas.commons.core.scheduler.ScheduledEventService;
import com.fincity.saas.commons.core.service.EventDefinitionService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

/**
 * {@code CoreServices.Event.ScheduleEvent} and {@code CancelScheduledEvent}: which client the one-off
 * is stored under. Blank is the caller's own client; another client must be the caller's DIRECT
 * managing client (the partner case), never a client the caller manages and never SYSTEM.
 *
 * <p>Fakes are hand-written: inline mocking cannot instrument classes on newer JDKs.
 */
class ScheduleEventClientCodeTest {

    private static final String APP = "leadzumptwo";

    /** Every scheduler call, as "schedule|cancel appCode clientCode key". */
    private List<String> calls;

    /** Every event definition read, as "appCode clientCode eventName". */
    private List<String> reads;

    private IFeignSecurityService securityService;
    private ScheduledEventService scheduledEventService;
    private EventDefinitionService edService;

    @BeforeEach
    void setUp() {
        this.calls = new ArrayList<>();
        this.reads = new ArrayList<>();

        // SYSTEM(1) above FIN(7) and OTHER(55); FIN above BP1(90). FIN has write access to the app;
        // SYSTEM has write access to every app, which must never lend it out.
        Map<String, Client> clients = Map.of(
                "SYSTEM", new Client().setId(BigInteger.ONE).setCode("SYSTEM").setTypeCode("SYS"),
                "FIN", new Client().setId(BigInteger.valueOf(7)).setCode("FIN").setTypeCode("BUS"),
                "BP1", new Client().setId(BigInteger.valueOf(90)).setCode("BP1").setTypeCode("BUS"),
                "OTHER", new Client().setId(BigInteger.valueOf(55)).setCode("OTHER").setTypeCode("BUS"));
        Map<BigInteger, List<BigInteger>> chains = Map.of(
                BigInteger.ONE, List.of(BigInteger.ONE),
                BigInteger.valueOf(7), List.of(BigInteger.valueOf(7), BigInteger.ONE),
                BigInteger.valueOf(90), List.of(BigInteger.valueOf(90), BigInteger.valueOf(7), BigInteger.ONE),
                BigInteger.valueOf(55), List.of(BigInteger.valueOf(55), BigInteger.ONE));

        this.securityService = (IFeignSecurityService) Proxy.newProxyInstance(
                IFeignSecurityService.class.getClassLoader(), new Class<?>[] {IFeignSecurityService.class},
                (p, m, a) -> switch (m.getName()) {
                    case "getClientByCode" -> Mono.justOrEmpty(clients.get((String) a[0]));
                    case "getClientHierarchy" -> Mono.justOrEmpty(chains.get((BigInteger) a[0]));
                    case "hasWriteAccess" -> Mono.just("SYSTEM".equals(a[1]) || (APP.equals(a[0]) && "FIN".equals(a[1])));
                    default -> throw new UnsupportedOperationException(m.getName());
                });

        this.scheduledEventService = new ScheduledEventService(null, null) {
            @Override
            public Mono<Scheduled> schedule(String appCode, String clientCode, boolean draft, String eventName,
                    Map<String, Object> data, Instant runAt, String key, String scheduledBy) {
                calls.add("schedule " + appCode + " " + clientCode + " " + key);
                return Mono.just(new Scheduled(key, new ScheduledEvent().setKey(key).setClientCode(clientCode)));
            }

            @Override
            public Mono<Boolean> cancel(String appCode, String clientCode, boolean draft, String key) {
                calls.add("cancel " + appCode + " " + clientCode + " " + key);
                return Mono.just(Boolean.TRUE);
            }
        };

        this.edService = new EventDefinitionService() {
            @Override
            public Mono<ObjectWithUniqueID<EventDefinition>> read(String name, String appCode, String clientCode) {
                reads.add(appCode + " " + clientCode + " " + name);
                return Mono.just(new ObjectWithUniqueID<>(new EventDefinition(), "u1"));
            }
        };
    }

    private static ContextAuthentication userOf(String clientCode) {
        ContextUser user = new ContextUser().setId(BigInteger.valueOf(110)).setClientId(BigInteger.ONE);
        ContextAuthentication ca = new ContextAuthentication().setUser(user);
        ca.setAuthenticated(true);
        return ca.setClientCode(clientCode)
                .setLoggedInFromClientCode(clientCode)
                .setUrlClientCode(clientCode)
                .setUrlAppCode(APP)
                .setAccessTokenExpiryAt(LocalDateTime.MAX);
    }

    private ScheduleEventFunction schedule() {
        return new ScheduleEventFunction(this.scheduledEventService, this.edService, this.securityService);
    }

    private CancelScheduledEventFunction cancel() {
        return new CancelScheduledEventFunction(this.scheduledEventService, this.securityService);
    }

    private static Map<String, JsonElement> scheduleArgs(String clientCode) {
        Map<String, JsonElement> args = new HashMap<>();
        args.put("eventName", new JsonPrimitive("taskReminder"));
        args.put("data", new JsonObject());
        args.put("runAt", new JsonPrimitive("2026-10-09T09:00:00Z"));
        args.put("key", new JsonPrimitive("task-1"));
        if (clientCode != null) args.put("clientCode", new JsonPrimitive(clientCode));
        return args;
    }

    private static Map<String, JsonElement> cancelArgs(String clientCode) {
        Map<String, JsonElement> args = new HashMap<>();
        args.put("key", new JsonPrimitive("task-1"));
        if (clientCode != null) args.put("clientCode", new JsonPrimitive(clientCode));
        return args;
    }

    private Mono<FunctionOutput> run(AbstractReactiveFunction fn, Map<String, JsonElement> args, String caller) {

        ReactiveFunctionExecutionParameters params = new ReactiveFunctionExecutionParameters(null, null);
        params.setArguments(args);

        Mono<FunctionOutput> out = fn instanceof ScheduleEventFunction s ? s.internalExecute(params)
                : ((CancelScheduledEventFunction) fn).internalExecute(params);

        return out.contextWrite(Context.of(DefinitionFunction.CONTEXT_KEY, "true"))
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                        Mono.just(new SecurityContextImpl(userOf(caller)))));
    }

    private static boolean forbidden(Throwable e) {
        return e instanceof GenericException g && g.getStatusCode() == HttpStatus.FORBIDDEN;
    }

    @Test
    @DisplayName("no clientCode, or a blank one, stores and cancels under the caller's own client")
    void ownClientByDefault() {
        StepVerifier.create(this.run(this.schedule(), scheduleArgs(null), "FIN"))
                .assertNext(fo -> assertTrue(fo.next().getResult().get("scheduled").getAsBoolean()))
                .verifyComplete();
        StepVerifier.create(this.run(this.schedule(), scheduleArgs(""), "BP1"))
                .expectNextCount(1)
                .verifyComplete();
        StepVerifier.create(this.run(this.cancel(), cancelArgs("FIN"), "FIN"))
                .assertNext(fo -> assertTrue(fo.next().getResult().get("cancelled").getAsBoolean()))
                .verifyComplete();

        assertEquals(List.of("schedule " + APP + " FIN task-1", "schedule " + APP + " BP1 task-1",
                "cancel " + APP + " FIN task-1"), this.calls);
        assertEquals(List.of(APP + " FIN taskReminder", APP + " BP1 taskReminder"), this.reads);
    }

    @Test
    @DisplayName("a partner schedules and cancels under its direct managing client, where FIN's users can reach it")
    void partnerUsesDirectManagingClient() {
        StepVerifier.create(this.run(this.schedule(), scheduleArgs("FIN"), "BP1"))
                .assertNext(fo -> assertEquals("task-1", fo.next().getResult().get("key").getAsString()))
                .verifyComplete();
        StepVerifier.create(this.run(this.cancel(), cancelArgs("FIN"), "BP1"))
                .assertNext(fo -> assertTrue(fo.next().getResult().get("cancelled").getAsBoolean()))
                .verifyComplete();

        assertEquals(List.of("schedule " + APP + " FIN task-1", "cancel " + APP + " FIN task-1"), this.calls);
        assertEquals(List.of(APP + " FIN taskReminder"), this.reads,
                "the event definition is read as the managing client sees it");
    }

    @Test
    @DisplayName("a client the caller manages is refused: managing a client is not acting as it")
    void managedClientRefused() {
        StepVerifier.create(this.run(this.schedule(), scheduleArgs("BP1"), "FIN"))
                .expectErrorMatches(ScheduleEventClientCodeTest::forbidden)
                .verify();
        StepVerifier.create(this.run(this.cancel(), cancelArgs("BP1"), "FIN"))
                .expectErrorMatches(ScheduleEventClientCodeTest::forbidden)
                .verify();

        assertTrue(this.calls.isEmpty());
        assertTrue(this.reads.isEmpty());
    }

    @Test
    @DisplayName("SYSTEM is refused both ways: as a target, and as a caller reaching down")
    void systemRefused() {
        for (String caller : List.of("FIN", "BP1", "OTHER")) {
            StepVerifier.create(this.run(this.schedule(), scheduleArgs("SYSTEM"), caller))
                    .expectErrorMatches(ScheduleEventClientCodeTest::forbidden)
                    .verify();
            StepVerifier.create(this.run(this.cancel(), cancelArgs("SYSTEM"), caller))
                    .expectErrorMatches(ScheduleEventClientCodeTest::forbidden)
                    .verify();
        }

        StepVerifier.create(this.run(this.schedule(), scheduleArgs("FIN"), "SYSTEM"))
                .expectErrorMatches(ScheduleEventClientCodeTest::forbidden)
                .verify();
        StepVerifier.create(this.run(this.cancel(), cancelArgs("FIN"), "SYSTEM"))
                .expectErrorMatches(ScheduleEventClientCodeTest::forbidden)
                .verify();

        assertTrue(this.calls.isEmpty());
    }

    @Test
    @DisplayName("an unrelated client and a grandparent are refused")
    void unrelatedRefused() {
        StepVerifier.create(this.run(this.schedule(), scheduleArgs("OTHER"), "FIN"))
                .expectErrorMatches(ScheduleEventClientCodeTest::forbidden)
                .verify();
        StepVerifier.create(this.run(this.cancel(), cancelArgs("FIN"), "OTHER"))
                .expectErrorMatches(ScheduleEventClientCodeTest::forbidden)
                .verify();

        assertTrue(this.calls.isEmpty());
    }
}
