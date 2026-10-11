package com.fincity.saas.commons.core.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.core.document.EventDefinition;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Everything the scheduler keeps, which is all in core's own Mongo: no leader
 * election and nothing held in memory that correctness depends on.
 *
 * <p>
 * schedule_run: one row per (appCode, clientCode, eventName, tickAt), claimed by
 * unique insert. scheduled_event: one row per one-off, claimed by an atomic
 * findAndModify.
 */
@Service
public class ScheduleStore {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleStore.class);

    static final String APP_CODE = "appCode";
    static final String CLIENT_CODE = "clientCode";
    static final String EVENT_NAME = "eventName";
    static final String TICK_AT = "tickAt";
    static final String STATUS = "status";
    static final String LEASE_UNTIL = "leaseUntil";
    static final String DRAFT = "draft";
    static final String KEY = "key";
    static final String RUN_AT = "runAt";

    public static final String RUN_UNIQUE_INDEX = "scheduleRunTickUnique";
    public static final String EVENT_KEY_UNIQUE_INDEX = "scheduledEventKeyUnique";

    private final ReactiveMongoTemplate mongo;
    private final EventSchedulerSettings settings;

    private final AtomicReference<Mono<Void>> indexes = new AtomicReference<>();

    public ScheduleStore(ReactiveMongoTemplate mongo, EventSchedulerSettings settings) {
        this.mongo = mongo;
        this.settings = settings;
    }

    /**
     * Creates the indexes once per instance and remembers success; a failure is
     * retried on the next call. The two unique indexes are what make claiming
     * safe, so nothing claims until they exist. The others only keep the
     * collections small and fast, and a failure there is logged and tolerated:
     * changing a retention property makes Mongo refuse the old TTL index, and
     * that must not stop every schedule.
     */
    public Mono<Void> ensureIndexes() {

        Mono<Void> current = this.indexes.get();
        if (current != null)
            return current;

        Mono<Void> attempt = this.createIndexes()
                .doOnError(e -> this.indexes.set(null))
                .cache();

        if (!this.indexes.compareAndSet(null, attempt))
            return this.indexes.get();

        return attempt;
    }

    private Mono<Void> createIndexes() {

        var runs = this.mongo.indexOps(ScheduleRun.COLLECTION);
        var events = this.mongo.indexOps(ScheduledEvent.COLLECTION);

        Mono<String> required = runs.ensureIndex(new Index()
                        .on(APP_CODE, Sort.Direction.ASC)
                        .on(CLIENT_CODE, Sort.Direction.ASC)
                        .on(EVENT_NAME, Sort.Direction.ASC)
                        .on(TICK_AT, Sort.Direction.ASC)
                        .unique()
                        .named(RUN_UNIQUE_INDEX))
                .then(events.ensureIndex(new Index()
                        .on(APP_CODE, Sort.Direction.ASC)
                        .on(CLIENT_CODE, Sort.Direction.ASC)
                        .on(DRAFT, Sort.Direction.ASC)
                        .on(KEY, Sort.Direction.ASC)
                        .unique()
                        .named(EVENT_KEY_UNIQUE_INDEX)));

        Flux<String> optional = Flux.concat(
                // Small: only RUNNING rows are in it, which is what the overlap check reads.
                this.tolerant(runs.ensureIndex(new Index()
                        .on(APP_CODE, Sort.Direction.ASC)
                        .on(CLIENT_CODE, Sort.Direction.ASC)
                        .on(EVENT_NAME, Sort.Direction.ASC)
                        .partial(PartialIndexFilter.of(Criteria.where(STATUS).is(ScheduleRun.Status.RUNNING.name())))
                        .named("scheduleRunRunning"))),
                this.tolerant(runs.ensureIndex(new Index()
                        .on("createdAt", Sort.Direction.ASC)
                        .expire(Duration.ofDays(this.settings.getRunRetentionDays()))
                        .named("scheduleRunExpiry"))),
                this.tolerant(events.ensureIndex(new Index()
                        .on(STATUS, Sort.Direction.ASC)
                        .on(RUN_AT, Sort.Direction.ASC)
                        .named("scheduledEventDue"))),
                this.tolerant(events.ensureIndex(new Index()
                        .on(STATUS, Sort.Direction.ASC)
                        .on(LEASE_UNTIL, Sort.Direction.ASC)
                        .named("scheduledEventLease"))),
                // PENDING and CLAIMED rows have no finishedAt, so only finished rows expire.
                this.tolerant(events.ensureIndex(new Index()
                        .on("finishedAt", Sort.Direction.ASC)
                        .expire(Duration.ofDays(this.settings.getOneOffRetentionDays()))
                        .named("scheduledEventExpiry"))));

        return required.thenMany(optional).then();
    }

    private Mono<String> tolerant(Mono<String> index) {
        return index.onErrorResume(e -> {
            logger.warn("Scheduler index not created: {}", e.getMessage());
            return Mono.empty();
        });
    }

    // ---------------------------------------------------------------- recurring

    /** One EventDefinition document, any client's, that carries a schedule of its own. */
    public record ScheduledDefinition(String appCode, String name, String clientCode, Boolean perClient) {
    }

    /**
     * Every stored EventDefinition document that sets a schedule, raw: not merged
     * and not filtered for publication. The scheduler resolves the effective
     * definition per client afterwards; this only says which (app, event) pairs
     * to look at and which clients hold a schedule document of their own.
     */
    public Flux<ScheduledDefinition> findScheduledDefinitions() {

        Query query = new Query(Criteria.where("schedule").ne(null));
        query.fields().include(APP_CODE, "name", CLIENT_CODE, "schedule.perClient");

        return this.mongo.find(query, Document.class, this.mongo.getCollectionName(EventDefinition.class))
                .map(d -> {
                    Object schedule = d.get("schedule");
                    Boolean perClient = schedule instanceof Document s ? s.getBoolean("perClient") : null;
                    return new ScheduledDefinition(d.getString(APP_CODE), d.getString("name"),
                            d.getString(CLIENT_CODE), perClient);
                });
    }

    /** Whether a tick of this schedule is RUNNING with its lease still good. */
    public Mono<Boolean> isRunning(String appCode, String clientCode, String eventName, Instant now) {

        return this.mongo.exists(new Query(Criteria.where(APP_CODE).is(appCode)
                .and(CLIENT_CODE).is(clientCode)
                .and(EVENT_NAME).is(eventName)
                .and(STATUS).is(ScheduleRun.Status.RUNNING.name())
                .and(LEASE_UNTIL).gt(now)), ScheduleRun.class);
    }

    /** The newest recorded tick of this schedule, whatever its status. */
    public Mono<ScheduleRun> lastRun(String appCode, String clientCode, String eventName) {

        return this.mongo.findOne(new Query(Criteria.where(APP_CODE).is(appCode)
                        .and(CLIENT_CODE).is(clientCode)
                        .and(EVENT_NAME).is(eventName))
                .with(Sort.by(Sort.Direction.DESC, TICK_AT))
                .limit(1), ScheduleRun.class);
    }

    /**
     * Claims the tick by inserting its row. True for the one instance whose
     * insert landed, false for every other: the duplicate key is the expected
     * outcome for all but one of them, not an error.
     */
    public Mono<Boolean> claim(ScheduleRun run) {

        if (run.getId() == null)
            run.setId(UUID.randomUUID().toString());

        return this.mongo.insert(run)
                .thenReturn(Boolean.TRUE)
                .onErrorResume(DuplicateKeyException.class, e -> Mono.just(Boolean.FALSE));
    }

    /** Closes a RUNNING row. A row already closed (or never claimed) is left alone. */
    public Mono<Boolean> finishRun(String runId, ScheduleRun.Status status, String error, Instant now) {

        Update update = new Update().set(STATUS, status.name()).set("finishedAt", now);
        if (error != null)
            update.set("error", error);

        return this.mongo.updateFirst(new Query(Criteria.where("_id").is(runId)
                                .and(STATUS).is(ScheduleRun.Status.RUNNING.name())), update, ScheduleRun.class)
                .map(r -> r.getModifiedCount() > 0);
    }

    // ---------------------------------------------------------------- one-off

    /**
     * Writes the one-off, replacing any row with the same key unless that row is
     * claimed and its lease is still good. That one is in flight, so it is left
     * to finish and the call answers empty.
     *
     * <p>
     * The filter cannot match an in-flight row, so the upsert tries to insert a
     * second row with the same key and the unique index refuses it.
     */
    public Mono<ScheduledEvent> upsertScheduledEvent(ScheduledEvent event, Instant now) {

        // Plain equalities at the top level: an upsert seeds the inserted row from them.
        Query query = new Query(Criteria.where(APP_CODE).is(event.getAppCode())
                .and(CLIENT_CODE).is(event.getClientCode())
                .and(DRAFT).is(event.isDraft())
                .and(KEY).is(event.getKey())
                .orOperator(
                        Criteria.where(STATUS).ne(ScheduledEvent.Status.CLAIMED.name()),
                        Criteria.where(LEASE_UNTIL).lt(now)));

        Update update = new Update()
                .set(EVENT_NAME, event.getEventName())
                .set("data", event.getData())
                .set(RUN_AT, event.getRunAt())
                .set(STATUS, ScheduledEvent.Status.PENDING.name())
                .set("attempts", 0)
                .set("maxAttempts", event.getMaxAttempts())
                .set("scheduledBy", event.getScheduledBy())
                .set("updatedAt", now)
                .unset("owner")
                .unset("claimToken")
                .unset(LEASE_UNTIL)
                .unset("lastError")
                .unset("finishedAt")
                .setOnInsert("createdAt", now);

        return this.mongo.findAndModify(query, update, FindAndModifyOptions.options().upsert(true).returnNew(true),
                        ScheduledEvent.class)
                .onErrorResume(DuplicateKeyException.class, e -> Mono.empty());
    }

    /** PENDING to CANCELLED. A claimed, finished or missing row is not cancelled. */
    public Mono<Boolean> cancelScheduledEvent(String appCode, String clientCode, boolean draft, String key,
            Instant now) {

        return this.mongo.updateFirst(new Query(Criteria.where(APP_CODE).is(appCode)
                                .and(CLIENT_CODE).is(clientCode)
                                .and(DRAFT).is(draft)
                                .and(KEY).is(key)
                                .and(STATUS).is(ScheduledEvent.Status.PENDING.name())),
                        new Update().set(STATUS, ScheduledEvent.Status.CANCELLED.name())
                                .set("finishedAt", now)
                                .set("updatedAt", now),
                        ScheduledEvent.class)
                .map(r -> r.getModifiedCount() > 0);
    }

    /**
     * Atomically claims the earliest due one-off: PENDING and due, or CLAIMED by
     * an instance whose lease ran out. Each claim counts as an attempt.
     */
    public Mono<ScheduledEvent> claimDueEvent(Instant now, String owner, Duration lease) {

        Query query = new Query(new Criteria().orOperator(
                Criteria.where(STATUS).is(ScheduledEvent.Status.PENDING.name()).and(RUN_AT).lte(now),
                Criteria.where(STATUS).is(ScheduledEvent.Status.CLAIMED.name()).and(LEASE_UNTIL).lt(now)))
                .with(Sort.by(Sort.Direction.ASC, RUN_AT));

        Update update = new Update()
                .set(STATUS, ScheduledEvent.Status.CLAIMED.name())
                .set("owner", owner)
                .set("claimToken", UUID.randomUUID().toString())
                .set(LEASE_UNTIL, now.plus(lease))
                .set("updatedAt", now)
                .inc("attempts", 1);

        return this.mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true),
                ScheduledEvent.class);
    }

    /** Up to {@code limit} claims in one go, stopping at the first empty one. */
    public Flux<ScheduledEvent> claimDueEvents(Instant now, String owner, Duration lease, int limit) {

        return Flux.range(0, Math.max(0, limit))
                .concatMap(i -> this.claimDueEvent(now, owner, lease).map(List::of).defaultIfEmpty(List.of()))
                .takeWhile(l -> !l.isEmpty())
                .flatMapIterable(l -> l);
    }

    /** CLAIMED to DONE, only under the claim that is finishing. */
    public Mono<Boolean> completeScheduledEvent(String id, String claimToken, Instant now) {

        return this.mongo.updateFirst(this.claimedBy(id, claimToken),
                        new Update().set(STATUS, ScheduledEvent.Status.DONE.name())
                                .set("finishedAt", now)
                                .set("updatedAt", now)
                                .unset(LEASE_UNTIL),
                        ScheduledEvent.class)
                .map(r -> r.getModifiedCount() > 0);
    }

    /**
     * CLAIMED back to PENDING at {@code retryAt}, or to FAILED when
     * {@code retryAt} is null. Only under the claim that is finishing, so a late
     * report from a claim that already lost its lease changes nothing.
     */
    public Mono<Boolean> failScheduledEvent(String id, String claimToken, Instant retryAt, String error,
            Instant now) {

        Update update = new Update().set("lastError", error).set("updatedAt", now).unset(LEASE_UNTIL);

        if (retryAt == null)
            update.set(STATUS, ScheduledEvent.Status.FAILED.name()).set("finishedAt", now);
        else
            update.set(STATUS, ScheduledEvent.Status.PENDING.name())
                    .set(RUN_AT, retryAt)
                    .unset("owner")
                    .unset("claimToken");

        return this.mongo.updateFirst(this.claimedBy(id, claimToken), update, ScheduledEvent.class)
                .map(r -> r.getModifiedCount() > 0);
    }

    public Mono<ScheduledEvent> findScheduledEvent(String id) {
        return this.mongo.findById(id, ScheduledEvent.class);
    }

    private Query claimedBy(String id, String claimToken) {
        return new Query(Criteria.where("_id").is(id)
                .and("claimToken").is(claimToken)
                .and(STATUS).is(ScheduledEvent.Status.CLAIMED.name()));
    }

    /** For tests and diagnosis: rows of one schedule, oldest first. */
    public Flux<ScheduleRun> runsOf(String appCode, String clientCode, String eventName) {
        return this.mongo.find(new Query(Criteria.where(APP_CODE).is(appCode)
                        .and(CLIENT_CODE).is(clientCode)
                        .and(EVENT_NAME).is(eventName))
                .with(Sort.by(Sort.Direction.ASC, TICK_AT)), ScheduleRun.class);
    }
}
