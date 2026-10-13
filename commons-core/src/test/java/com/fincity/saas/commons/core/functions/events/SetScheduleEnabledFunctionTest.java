package com.fincity.saas.commons.core.functions.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.saas.commons.core.document.EventDefinition;
import com.fincity.saas.commons.core.model.EventSchedule;
import com.fincity.saas.commons.core.repository.EventDefinitionRepository;
import com.fincity.saas.commons.core.service.EventDefinitionService;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.util.LogUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

/**
 * {@code CoreServices.Event.SetScheduleEnabled} and the write behind it,
 * {@link EventDefinitionService#setOwnScheduleEnabled}: which copy is written,
 * that only {@code enabled} goes into it, and when nothing is written at all.
 *
 * <p>Fakes are hand-written: inline mocking cannot instrument classes on newer JDKs.
 */
class SetScheduleEnabledFunctionTest {

    private static final String APP = "leadzumptwo";
    private static final String EVENT = "LZ_WA_OUTBOX";

    /** The effective definition each client sees, keyed "appCode clientCode name". */
    private Map<String, EventDefinition> effective;

    /** Each client's own stored copy, keyed the same way. */
    private Map<String, EventDefinition> stored;

    private List<EventDefinition> saved;
    private List<String> evicted;

    private EventDefinitionService edService;

    private static String key(String appCode, String clientCode, String name) {
        return appCode + " " + clientCode + " " + name;
    }

    private static EventDefinition withSchedule(EventSchedule schedule) {
        return new EventDefinition().setSchedule(schedule);
    }

    private static EventSchedule baseSchedule(Boolean enabled) {
        return new EventSchedule().setCron("0 */15 * * * ?").setTimezone("UTC").setPerClient(true).setSpread(true)
                .setEnabled(enabled);
    }

    @BeforeEach
    void setUp() {
        this.effective = new HashMap<>();
        this.stored = new HashMap<>();
        this.saved = new ArrayList<>();
        this.evicted = new ArrayList<>();

        EventDefinitionRepository fakeRepo = (EventDefinitionRepository) Proxy.newProxyInstance(
                EventDefinitionRepository.class.getClassLoader(), new Class<?>[] {EventDefinitionRepository.class},
                (p, m, a) -> switch (m.getName()) {
                    case "findOneByNameAndAppCodeAndClientCode" ->
                        Mono.justOrEmpty(this.stored.get(key((String) a[1], (String) a[2], (String) a[0])));
                    case "save" -> {
                        this.saved.add((EventDefinition) a[0]);
                        yield Mono.just(a[0]);
                    }
                    default -> throw new UnsupportedOperationException(m.getName());
                });

        this.edService = new EventDefinitionService() {
            {
                this.repo = fakeRepo;
            }

            @Override
            public Mono<EventDefinition> readEffective(String name, String appCode, String clientCode) {
                return Mono.justOrEmpty(effective.get(key(appCode, clientCode, name)));
            }

            @Override
            protected Mono<EventDefinition> evictRecursively(EventDefinition f) {
                evicted.add(key(f.getAppCode(), f.getClientCode(), f.getName()));
                return Mono.just(f);
            }
        };
    }

    private static ContextAuthentication eventRunnerFor(String clientCode) {
        ContextUser user = new ContextUser().setId(BigInteger.ZERO).setClientId(BigInteger.valueOf(7));
        ContextAuthentication ca = new ContextAuthentication().setUser(user);
        return ca.setClientCode(clientCode)
                .setLoggedInFromClientCode(clientCode)
                .setUrlClientCode(clientCode)
                .setUrlAppCode(APP)
                .setAccessTokenExpiryAt(LocalDateTime.MAX);
    }

    private Mono<FunctionOutput> run(Boolean enabled, String clientCode, Context extra) {

        Map<String, JsonElement> args = new HashMap<>();
        args.put("eventName", new JsonPrimitive(EVENT));
        if (enabled != null)
            args.put("enabled", new JsonPrimitive(enabled));

        ReactiveFunctionExecutionParameters params = new ReactiveFunctionExecutionParameters(null, null);
        params.setArguments(args);

        return new SetScheduleEnabledFunction(this.edService).internalExecute(params)
                .contextWrite(extra)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(
                        Mono.just(new SecurityContextImpl(eventRunnerFor(clientCode)))));
    }

    private Mono<FunctionOutput> runInDefinition(Boolean enabled, String clientCode) {
        return this.run(enabled, clientCode, Context.of(DefinitionFunction.CONTEXT_KEY, "true"));
    }

    private static boolean changed(FunctionOutput fo) {
        return fo.next().getResult().get("changed").getAsBoolean();
    }

    @Test
    @DisplayName("a client without a copy gets one holding only enabled, under its own code and the caller's app")
    void createsACopyWithOnlyEnabled() {
        // AKASH sees FIN's copy, so that is the one its new copy overrides.
        this.effective.put(key(APP, "AKASH", EVENT),
                (EventDefinition) withSchedule(baseSchedule(false)).setClientCode("FIN"));

        StepVerifier.create(this.runInDefinition(null, "AKASH"))
                .assertNext(fo -> assertTrue(changed(fo)))
                .verifyComplete();

        assertEquals(1, this.saved.size());
        EventDefinition copy = this.saved.getFirst();
        assertEquals(EVENT, copy.getName());
        assertEquals(APP, copy.getAppCode());
        assertEquals("AKASH", copy.getClientCode());
        assertEquals(Boolean.TRUE, copy.getSchedule().getEnabled());
        assertNull(copy.getSchedule().getCron(), "cron stays with the base");
        assertNull(copy.getSchedule().getPerClient(), "perClient stays with the base");
        assertNull(copy.getSchema(), "the schema stays with the base");
        assertEquals("FIN", copy.getBaseClientCode(),
                "without the link the copy is read on its own, has no cron and never runs");
        assertEquals(List.of(key(APP, "AKASH", EVENT)), this.evicted);
    }

    @Test
    @DisplayName("an existing copy keeps what it overrides and only enabled moves")
    void updatesAnExistingCopy() {
        this.effective.put(key(APP, "FIN", EVENT), withSchedule(baseSchedule(true)));
        EventDefinition own = (EventDefinition) new EventDefinition()
                .setSchedule(new EventSchedule().setCron("0 */5 * * * ?").setEnabled(true))
                .setName(EVENT).setAppCode(APP).setClientCode("FIN");
        own.setVersion(3);
        this.stored.put(key(APP, "FIN", EVENT), own);

        StepVerifier.create(this.runInDefinition(false, "FIN"))
                .assertNext(fo -> assertTrue(changed(fo)))
                .verifyComplete();

        EventDefinition copy = this.saved.getFirst();
        assertEquals(Boolean.FALSE, copy.getSchedule().getEnabled());
        assertEquals("0 */5 * * * ?", copy.getSchedule().getCron());
        assertEquals(4, copy.getVersion());
    }

    @Test
    @DisplayName("a client already in that state is left alone")
    void noChangeWritesNothing() {
        this.effective.put(key(APP, "FIN", EVENT), withSchedule(baseSchedule(true)));
        this.effective.put(key(APP, "AKHIL", EVENT), withSchedule(baseSchedule(null)));

        StepVerifier.create(this.runInDefinition(true, "FIN"))
                .assertNext(fo -> assertFalse(changed(fo)))
                .verifyComplete();
        StepVerifier.create(this.runInDefinition(true, "AKHIL"))
                .assertNext(fo -> assertFalse(changed(fo)))
                .verifyComplete();

        assertTrue(this.saved.isEmpty(), "absent enabled already means enabled");
    }

    @Test
    @DisplayName("an event the app does not define, or one without a schedule, writes nothing")
    void unknownOrUnscheduledEventWritesNothing() {
        this.effective.put(key(APP, "FIN", EVENT), new EventDefinition());

        StepVerifier.create(this.runInDefinition(true, "FIN"))
                .assertNext(fo -> assertFalse(changed(fo)))
                .verifyComplete();
        StepVerifier.create(this.runInDefinition(true, "NOBODY"))
                .assertNext(fo -> assertFalse(changed(fo)))
                .verifyComplete();

        assertTrue(this.saved.isEmpty());
    }

    @Test
    @DisplayName("outside a server function definition it does nothing")
    void outsideADefinitionDoesNothing() {
        this.effective.put(key(APP, "AKASH", EVENT), withSchedule(baseSchedule(false)));

        StepVerifier.create(this.run(true, "AKASH", Context.empty()))
                .assertNext(fo -> assertFalse(changed(fo)))
                .verifyComplete();

        assertTrue(this.saved.isEmpty());
    }

    @Test
    @DisplayName("on the draft surface the live definition is left alone")
    void draftSurfaceDoesNothing() {
        this.effective.put(key(APP, "AKASH", EVENT), withSchedule(baseSchedule(false)));

        StepVerifier.create(this.run(true, "AKASH",
                Context.of(DefinitionFunction.CONTEXT_KEY, "true", LogUtil.DRAFT_KEY, Boolean.TRUE)))
                .assertNext(fo -> assertFalse(changed(fo)))
                .verifyComplete();

        assertTrue(this.saved.isEmpty());
    }
}
