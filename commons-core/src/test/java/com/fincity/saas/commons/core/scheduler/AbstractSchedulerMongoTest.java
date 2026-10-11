package com.fincity.saas.commons.core.scheduler;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.testcontainers.containers.MongoDBContainer;

import com.fincity.saas.commons.core.document.EventDefinition;
import com.fincity.saas.commons.core.model.EventSchedule;
import com.fincity.saas.commons.core.service.EventDefinitionService;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.util.LogUtil;
import com.mongodb.reactivestreams.client.MongoClients;

import reactor.core.publisher.Mono;

/**
 * A real Mongo, because the scheduler's guarantee across instances is a unique
 * index refusing the second insert and an atomic findAndModify, and a mock of
 * the template would only restate the assumption. Everything that is not Mongo
 * is a small fake.
 *
 * <p>
 * Each "instance" in a test is its own {@link EventScheduler} over its own
 * {@link ScheduleStore}, sharing nothing but the database, as two core JVMs do.
 */
public abstract class AbstractSchedulerMongoTest {

    protected static final String APP = "leadzumptwo";

    @SuppressWarnings("resource")
    private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    private static ReactiveMongoTemplate template;

    protected static synchronized ReactiveMongoTemplate mongo() {
        if (template != null)
            return template;
        MONGO.start();
        template = new ReactiveMongoTemplate(MongoClients.create(MONGO.getReplicaSetUrl()), "schedulertest");
        return template;
    }

    protected final MutableClock clock = new MutableClock(Instant.parse("2026-10-09T09:00:10Z"));
    protected final FakeDefinitions definitions = new FakeDefinitions();
    protected final FakeEvents events = new FakeEvents();
    protected final Map<String, List<String>> writeAccess = new ConcurrentHashMap<>();
    protected final AtomicInteger writeAccessCalls = new AtomicInteger();

    @BeforeEach
    void resetDatabase() {
        mongo().getMongoDatabase()
                .flatMap(db -> Mono.from(db.drop()))
                .block();
    }

    protected EventSchedulerSettings settings() {
        return new EventSchedulerSettings()
                .setTickMs(30_000)
                .setLeaseSeconds(300)
                .setCatchUpWindowMinutes(1500)
                .setClientsRefreshSeconds(300)
                .setConcurrency(8)
                .setOneOffBatchSize(500)
                .setOneOffMaxAttempts(3)
                .setOneOffBackoffBaseSeconds(30)
                .setOneOffBackoffMaxSeconds(3600);
    }

    /** One core instance: its own store, scheduler and one-off service over the shared database. */
    protected record Instance(EventScheduler scheduler, ScheduledEventService oneOffs, ScheduleStore store) {
    }

    protected Instance instance() {
        return this.instance(this.settings());
    }

    protected Instance instance(EventSchedulerSettings settings) {
        ScheduleStore store = new ScheduleStore(mongo(), settings);
        ScheduledEventService oneOffs = new ScheduledEventService(store, settings);
        oneOffs.setClock(this.clock);
        EventScheduler scheduler = new EventScheduler(this.definitions, this.security(), this.events, store, oneOffs,
                settings);
        scheduler.setClock(this.clock);
        return new Instance(scheduler, oneOffs, store);
    }

    /** A stored EventDefinition document carrying a schedule, as the scheduler's discovery query sees it. */
    protected void storeDefinition(String clientCode, String eventName, EventSchedule schedule) {
        EventDefinition ed = new EventDefinition();
        ed.setName(eventName);
        ed.setAppCode(APP);
        ed.setClientCode(clientCode);
        ed.setSchedule(schedule);
        mongo().insert(ed).block();
    }

    protected static EventDefinition effective(String eventName, EventSchedule schedule) {
        EventDefinition ed = new EventDefinition();
        ed.setName(eventName);
        ed.setAppCode(APP);
        ed.setSchedule(schedule);
        return ed;
    }

    protected List<ScheduleRun> runs(String clientCode, String eventName) {
        return mongo().find(new org.springframework.data.mongodb.core.query.Query(
                        org.springframework.data.mongodb.core.query.Criteria.where("clientCode").is(clientCode)
                                .and("eventName").is(eventName))
                .with(org.springframework.data.domain.Sort.by("tickAt")), ScheduleRun.class)
                .collectList().block();
    }

    private IFeignSecurityService security() {
        return (IFeignSecurityService) Proxy.newProxyInstance(IFeignSecurityService.class.getClassLoader(),
                new Class<?>[] { IFeignSecurityService.class }, (proxy, method, args) -> {
                    if (method.getName().equals("getWriteAccessClientCodes")) {
                        this.writeAccessCalls.incrementAndGet();
                        return Mono.just(this.writeAccess.getOrDefault((String) args[0], List.of()));
                    }
                    if (method.getName().equals("toString"))
                        return "FakeSecurity";
                    if (method.getName().equals("hashCode"))
                        return System.identityHashCode(proxy);
                    if (method.getName().equals("equals"))
                        return proxy == args[0];
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    /** Effective definitions per (event, client), standing in for the overridable read. */
    protected static class FakeDefinitions extends EventDefinitionService {

        private final Map<String, EventDefinition> byClient = new ConcurrentHashMap<>();

        void put(String clientCode, EventDefinition ed) {
            this.byClient.put(ed.getName() + "/" + clientCode, ed);
        }

        @Override
        public Mono<EventDefinition> readEffective(String name, String appCode, String clientCode) {
            EventDefinition ed = this.byClient.get(name + "/" + clientCode);
            return ed == null ? Mono.empty() : Mono.just(new EventDefinition(ed));
        }
    }

    /** Records every raised event instead of publishing it, and can be told to fail. */
    protected static class FakeEvents extends EventCreationService {

        final List<EventQueObject> raised = new CopyOnWriteArrayList<>();
        final List<Boolean> draftFlags = new CopyOnWriteArrayList<>();
        volatile boolean failing;

        @Override
        public Mono<Boolean> createEvent(EventQueObject queObj) {
            return Mono.deferContextual(cv -> {
                if (this.failing)
                    return Mono.error(new IllegalStateException("broker down"));
                this.raised.add(queObj);
                this.draftFlags.add(Boolean.TRUE.equals(cv.getOrDefault(LogUtil.DRAFT_KEY, Boolean.FALSE)));
                return Mono.just(Boolean.TRUE);
            });
        }

        List<EventQueObject> of(String clientCode, String eventName) {
            return this.raised.stream()
                    .filter(q -> clientCode.equals(q.getClientCode()) && eventName.equals(q.getEventName()))
                    .toList();
        }
    }

    protected static class MutableClock extends Clock {

        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant now) {
            this.now = now;
        }

        void set(String iso) {
            this.now = Instant.parse(iso);
        }

        void advanceSeconds(long seconds) {
            this.now = this.now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return this.now;
        }
    }
}
