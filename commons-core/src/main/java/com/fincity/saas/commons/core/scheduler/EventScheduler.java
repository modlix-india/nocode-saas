package com.fincity.saas.commons.core.scheduler;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.core.model.EventSchedule;
import com.fincity.saas.commons.core.jooq.enums.CoreEventRunsStatus;
import com.fincity.saas.commons.core.service.EventDefinitionService;
import com.fincity.saas.commons.core.service.EventRunService;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.util.LogUtil;

import lombok.Getter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

/**
 * Raises events on a schedule. The schedule sits on the EventDefinition and the
 * work is whatever the same-named EventAction does, so a tick goes through
 * EventCreationService into the events queue and EventsQueListener runs it
 * exactly as it runs an event raised by CoreServices.Event.CreateEvent.
 *
 * <p>
 * Every core instance runs a pass every {@code core.scheduler.tickMs}; Mongo
 * decides which one does each piece of work (see {@link ScheduleStore}). A pass:
 * <ol>
 * <li>Finds every stored EventDefinition that carries a schedule, grouped by
 * (app, event).</li>
 * <li>Works out which clients run it. A {@code perClient} schedule runs for every
 * client with write access to the app; otherwise it runs for the client holding
 * the definition. Each client's EFFECTIVE definition decides, so an override's
 * cron, timezone and enabled beat the base's. A client that is deleted, inactive
 * or locked, or is managed by one that is, runs nothing; its due one-offs are
 * closed as FAILED with that reason.</li>
 * <li>Takes the latest tick on that client's cron grid. A tick inside the fresh
 * window is the current one. An older one is a missed tick, and is caught up
 * only when a run was recorded before it, which caps an outage at one catch-up
 * run and keeps a brand-new schedule from firing for a tick it never had.</li>
 * <li>Claims the tick by unique insert, one (client, tick) at a time and only
 * while this instance has a free run slot ({@code core.scheduler.runConcurrency}).
 * If the previous tick is still RUNNING inside its lease, the row goes in as
 * SKIPPED instead, so a slow sweep never runs twice at once. Only the instance
 * whose insert landed runs the tick. Candidates are taken in a shuffled order so
 * two instances rarely reach for the same client at once.</li>
 * <li>Claims due one-offs from scheduled_event the same way, one per free slot.</li>
 * </ol>
 *
 * <p>
 * The claiming instance runs the work itself, through {@link ScheduledEventRunner}
 * (core's EventsQueListener), in the background: a pass only claims and starts,
 * and a slot frees when its run finishes. So with several core instances each
 * takes as much as it has room for and no instance is pinned to any client. A
 * service without a runner publishes the event to the queue instead.
 *
 * <p>
 * How a run finishes: the raised event carries {@code _schedule} in its data,
 * and EventsQueListener hands the outcome of its tasks to
 * {@link #trackCompletion}, which marks the run DONE or FAILED (or, for a
 * one-off, DONE or back to PENDING with backoff). A listener that dies mid-run
 * reports nothing, and the lease running out is what frees the schedule.
 *
 * <p>
 * Events are raised without a user, so the listener runs their tasks with its
 * anonymous context for the client, as it does for any event raised without
 * authentication.
 */
@Component
public class EventScheduler {

    private static final Logger logger = LoggerFactory.getLogger(EventScheduler.class);

    /** The key in a raised event's data that says which run it belongs to. */
    public static final String SCHEDULE_DATA_KEY = "_schedule";

    public static final String KIND_CRON = "cron";
    public static final String KIND_ONCE = "once";

    private final EventDefinitionService edService;
    private final IFeignSecurityService securityService;
    private final EventCreationService ecService;
    private final ScheduleStore store;
    private final ScheduledEventService oneOffs;
    private final EventSchedulerSettings settings;

    /**
     * The event run log, for the outcomes that never reach an EventAction: a tick
     * skipped because the previous one was still going, a raise that failed, a
     * one-off given up on. Runs that do reach one are logged by the runner. Null in
     * tests that do not care.
     */
    private final EventRunService eventRuns;

    @Getter
    private final String instanceId;

    private final AtomicBoolean passRunning = new AtomicBoolean(false);

    /** Resolved lazily: the runner (EventsQueListener) itself depends on this scheduler. */
    private final ObjectProvider<ScheduledEventRunner> runnerProvider;

    private ScheduledEventRunner runnerOverride;

    /** Runs started by this instance and not finished yet; capped at runConcurrency. */
    private final AtomicInteger inFlight = new AtomicInteger();

    /**
     * The last tick this instance has dealt with, per schedule and client. Only
     * spares the database a repeat insert every pass between two ticks; the
     * unique index is what guarantees one run, and losing this map changes
     * nothing but the query count.
     */
    private final Map<String, Instant> handled = new ConcurrentHashMap<>();

    private final Map<String, WriteAccessClients> writeAccess = new ConcurrentHashMap<>();

    private final Map<String, CronExpression> crons = new ConcurrentHashMap<>();

    /** What security last said about each client, reused for clientsRefreshSeconds. */
    private final Map<String, ClientState> clientStates = new ConcurrentHashMap<>();

    private Clock clock = Clock.systemUTC();

    private record WriteAccessClients(Set<String> codes, Instant fetchedAt) {
    }

    private record ClientState(boolean active, Instant fetchedAt) {
    }

    public EventScheduler(EventDefinitionService edService, IFeignSecurityService securityService,
            EventCreationService ecService, ScheduleStore store, ScheduledEventService oneOffs,
            EventSchedulerSettings settings) {
        this(edService, securityService, ecService, store, oneOffs, settings, null, null);
    }

    public EventScheduler(EventDefinitionService edService, IFeignSecurityService securityService,
            EventCreationService ecService, ScheduleStore store, ScheduledEventService oneOffs,
            EventSchedulerSettings settings, ObjectProvider<ScheduledEventRunner> runnerProvider) {
        this(edService, securityService, ecService, store, oneOffs, settings, runnerProvider, null);
    }

    @Autowired
    public EventScheduler(EventDefinitionService edService, IFeignSecurityService securityService,
            EventCreationService ecService, ScheduleStore store, ScheduledEventService oneOffs,
            EventSchedulerSettings settings, ObjectProvider<ScheduledEventRunner> runnerProvider,
            EventRunService eventRuns) {
        this.runnerProvider = runnerProvider;
        this.eventRuns = eventRuns;
        this.edService = edService;
        this.securityService = securityService;
        this.ecService = ecService;
        this.store = store;
        this.oneOffs = oneOffs;
        this.settings = settings;

        String host = System.getenv("HOSTNAME");
        this.instanceId = (host == null || host.isBlank() ? "core" : host) + "-"
                + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Tests only. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    /** Tests only: run claimed work through this runner. */
    void setRunner(ScheduledEventRunner runner) {
        this.runnerOverride = runner;
    }

    /** Runs this instance has started and not finished. */
    public int inFlight() {
        return this.inFlight.get();
    }

    private ScheduledEventRunner runner() {
        if (this.runnerOverride != null)
            return this.runnerOverride;
        return this.runnerProvider == null ? null : this.runnerProvider.getIfAvailable();
    }

    private boolean tryTakeSlot() {
        int max = Math.max(1, this.settings.getRunConcurrency());
        while (true) {
            int now = this.inFlight.get();
            if (now >= max)
                return false;
            if (this.inFlight.compareAndSet(now, now + 1))
                return true;
        }
    }

    private void releaseSlot() {
        this.inFlight.decrementAndGet();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!this.settings.isEnabled())
            return;
        this.store.ensureIndexes()
                .subscribe(v -> {
                }, e -> logger.error("Scheduler indexes could not be created; no schedule runs until they are", e));
    }

    @Scheduled(initialDelayString = "${core.scheduler.initialDelayMs:30000}",
            fixedDelayString = "${core.scheduler.tickMs:30000}")
    public void onSchedule() {

        if (!this.settings.isEnabled() || !this.passRunning.compareAndSet(false, true))
            return;

        this.pass()
                .doFinally(s -> this.passRunning.set(false))
                .subscribe(r -> {
                    if (r.raised() + r.skipped() + r.failed() + r.oneOffsRaised() > 0)
                        logger.info("Scheduler pass on {}: {}", this.instanceId, r);
                }, e -> logger.error("Scheduler pass failed on {}", this.instanceId, e));
    }

    /** What one pass did on this instance. */
    /**
     * raised: ticks this instance claimed and started (or published). busy: due
     * ticks left for another instance or the next pass because every run slot
     * here was taken.
     */
    public record PassReport(int raised, int skipped, int lost, int failed, int oneOffsRaised,
            int oneOffsFailed, int busy) {
    }

    private static final class Counts {
        final AtomicInteger raised = new AtomicInteger();
        final AtomicInteger skipped = new AtomicInteger();
        final AtomicInteger lost = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();
        final AtomicInteger oneOffsRaised = new AtomicInteger();
        final AtomicInteger oneOffsFailed = new AtomicInteger();
        final AtomicInteger busy = new AtomicInteger();

        PassReport report() {
            return new PassReport(raised.get(), skipped.get(), lost.get(), failed.get(), oneOffsRaised.get(),
                    oneOffsFailed.get(), busy.get());
        }
    }

    /** One pass at the current time. Nothing claims until the unique indexes exist. */
    public Mono<PassReport> pass() {

        Instant now = this.clock.instant();
        Counts counts = new Counts();

        return this.store.ensureIndexes()
                .then(this.recurring(now, counts))
                .then(this.dueOneOffs(now, counts))
                .then(Mono.fromSupplier(counts::report))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "EventScheduler.pass"));
    }

    // ---------------------------------------------------------------- recurring

    /** One (app, event, client) a pass looks at. */
    private record Candidate(String appCode, String eventName, String clientCode, boolean holder,
            boolean hasWriteAccess) {
    }

    private Mono<Void> recurring(Instant now, Counts counts) {

        return this.store.findScheduledDefinitions()
                .collectList()
                .flatMapMany(docs -> Flux.fromIterable(docs.stream()
                        .collect(Collectors.groupingBy(d -> d.appCode() + "\u0000" + d.name(), LinkedHashMap::new,
                                Collectors.toList()))
                        .values()))
                .concatMap(group -> this.candidates(group, now)
                        .onErrorResume(e -> {
                            logger.warn("Scheduler skipped {}.{}: {}", group.getFirst().appCode(),
                                    group.getFirst().name(), e.getMessage());
                            return Flux.empty();
                        }))
                .collectList()
                // No run for a client that is deleted, inactive or locked, or is managed by
                // one that is, whether it holds the schedule itself or is fanned out to.
                .flatMap(list -> this.inactiveClients(list.stream().map(Candidate::clientCode)
                        .collect(Collectors.toSet()), now)
                        .map(inactive -> inactive.isEmpty() ? list
                                : list.stream().filter(c -> !inactive.contains(c.clientCode())).toList()))
                // Every instance walks the same candidates; a shuffled order keeps two of
                // them from reaching for the same client at the same moment.
                .flatMapMany(list -> {
                    List<Candidate> shuffled = new ArrayList<>(list);
                    Collections.shuffle(shuffled, ThreadLocalRandom.current());
                    return Flux.fromIterable(shuffled);
                })
                .flatMap(c -> this.runClient(c.appCode(), c.eventName(), c.clientCode(), c.holder(),
                        c.hasWriteAccess(), now, counts), Math.max(1, this.settings.getConcurrency()))
                .then();
    }

    private Flux<Candidate> candidates(List<ScheduleStore.ScheduledDefinition> group, Instant now) {

        String appCode = group.getFirst().appCode();
        String eventName = group.getFirst().name();

        // The clients that hold a schedule document of their own. A schedule that
        // is not perClient runs for these and nobody else.
        Set<String> holders = group.stream()
                .map(ScheduleStore.ScheduledDefinition::clientCode)
                .collect(Collectors.toCollection(HashSet::new));

        // Security is asked only when some document in the chain turns perClient
        // on; an app with only single-run schedules never fans out.
        boolean anyPerClient = group.stream().anyMatch(d -> Boolean.TRUE.equals(d.perClient()));

        Mono<Set<String>> writers = anyPerClient ? this.writeAccessClients(appCode, now) : Mono.just(Set.of());

        return writers.flatMapMany(w -> {
            Set<String> clients = new HashSet<>(holders);
            clients.addAll(w);
            return Flux.fromIterable(clients)
                    .map(client -> new Candidate(appCode, eventName, client, holders.contains(client),
                            w.contains(client)));
        });
    }

    private Mono<Void> runClient(String appCode, String eventName, String clientCode, boolean holder,
            boolean hasWriteAccess, Instant now, Counts counts) {

        return this.edService.readEffective(eventName, appCode, clientCode)
                .flatMap(ed -> {
                    EventSchedule schedule = ed.getSchedule();
                    if (schedule == null || !schedule.isActive())
                        return Mono.empty();

                    // The client's own effective perClient decides, not the base's.
                    if (schedule.isForEachClient() ? !hasWriteAccess : !holder)
                        return Mono.empty();

                    CronExpression cron;
                    ZoneId zone;
                    try {
                        cron = this.crons.computeIfAbsent(schedule.getCron(), CronGrid::parse);
                        zone = ZoneId.of(schedule.effectiveTimezone());
                    } catch (RuntimeException e) {
                        logger.warn("Scheduler cannot read the schedule of {}.{} for {}: {}", appCode, eventName,
                                clientCode, e.getMessage());
                        return Mono.empty();
                    }

                    Instant tick = schedule.isSpreadAcrossPeriod()
                            ? CronGrid.latestSpreadTick(cron, zone, now, this.settings.catchUpWindow(),
                                    appCode + "\u0000" + clientCode + "\u0000" + eventName)
                            : CronGrid.latestTick(cron, zone, now, this.settings.catchUpWindow());
                    if (tick == null)
                        return Mono.empty();

                    String memoKey = appCode + "\u0000" + clientCode + "\u0000" + eventName;
                    if (tick.equals(this.handled.get(memoKey)))
                        return Mono.empty();

                    boolean fresh = !tick.isBefore(now.minus(this.settings.freshWindow()));

                    Mono<Boolean> due = fresh ? Mono.just(Boolean.TRUE)
                            : this.store.lastRun(appCode, clientCode, eventName)
                                    .map(last -> last.getTickAt().isBefore(tick))
                                    // No run ever recorded: a new schedule, not a missed tick.
                                    .defaultIfEmpty(Boolean.FALSE);

                    return due.flatMap(go -> {
                        if (!Boolean.TRUE.equals(go)) {
                            this.handled.put(memoKey, tick);
                            return Mono.empty();
                        }
                        return this.claimAndRaise(appCode, clientCode, eventName, tick, now, counts)
                                .doOnSuccess(v -> this.handled.put(memoKey, tick));
                    });
                })
                .onErrorResume(SlotsFull.class, e -> Mono.empty())
                .onErrorResume(e -> {
                    logger.warn("Scheduler skipped {}.{} for {}: {}", appCode, eventName, clientCode,
                            e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> claimAndRaise(String appCode, String clientCode, String eventName, Instant tick,
            Instant now, Counts counts) {

        ScheduledEventRunner runner = this.runner();

        // In-process: claim only with a free slot. Without one the tick is left for
        // another instance (or this one's next pass), and is not marked handled.
        if (runner != null && !this.tryTakeSlot()) {
            counts.busy.incrementAndGet();
            return Mono.error(new SlotsFull());
        }

        AtomicBoolean slotHeld = new AtomicBoolean(runner != null);
        Runnable release = () -> {
            if (slotHeld.compareAndSet(true, false))
                this.releaseSlot();
        };

        return this.store.isRunning(appCode, clientCode, eventName, now)
                .flatMap(busy -> {
                    ScheduleRun run = new ScheduleRun()
                            .setId(UUID.randomUUID().toString())
                            .setAppCode(appCode)
                            .setClientCode(clientCode)
                            .setEventName(eventName)
                            .setTickAt(tick)
                            .setOwner(this.instanceId)
                            .setCreatedAt(now);

                    if (Boolean.TRUE.equals(busy))
                        run.setStatus(ScheduleRun.Status.SKIPPED).setFinishedAt(now);
                    else
                        run.setStatus(ScheduleRun.Status.RUNNING).setLeaseUntil(now.plus(this.settings.lease()));

                    return this.store.claim(run).flatMap(won -> {
                        if (!Boolean.TRUE.equals(won)) {
                            counts.lost.incrementAndGet();
                            release.run();
                            return Mono.empty();
                        }

                        Map<String, Object> marker = new HashMap<>();
                        marker.put("kind", KIND_CRON);
                        marker.put("runId", run.getId());
                        marker.put("tickAt", tick.toString());

                        Map<String, Object> data = new HashMap<>();
                        data.put(SCHEDULE_DATA_KEY, marker);

                        if (run.getStatus() == ScheduleRun.Status.SKIPPED) {
                            counts.skipped.incrementAndGet();
                            release.run();
                            return this.logRun(this.queObject(appCode, clientCode, eventName, data, false),
                                    CoreEventRunsStatus.SKIPPED,
                                    "Skipped: the run for the previous tick was still going.");
                        }

                        if (runner != null) {
                            counts.raised.incrementAndGet();
                            this.runDetached(runner, this.queObject(appCode, clientCode, eventName, data, false),
                                    release);
                            return Mono.empty();
                        }

                        return this.raise(appCode, clientCode, eventName, data, false)
                                .doOnSuccess(v -> counts.raised.incrementAndGet())
                                .then()
                                .onErrorResume(e -> {
                                    counts.failed.incrementAndGet();
                                    logger.warn("Scheduler could not raise {}.{} for {}: {}", appCode, eventName,
                                            clientCode, e.getMessage());
                                    return this.store.finishRun(run.getId(), ScheduleRun.Status.FAILED,
                                            "Raise failed: " + e.getMessage(), now)
                                            .then(this.logRun(
                                                    this.queObject(appCode, clientCode, eventName, data, false),
                                                    CoreEventRunsStatus.FAILED,
                                                    "The scheduler could not raise the event: "
                                                            + EventRunService.errorOf(e)));
                                });
                    });
                })
                .doOnError(e -> release.run())
                .doOnCancel(release)
                .then();
    }

    /** Signals that every run slot here is taken; runClient turns it into "not handled yet". */
    private static final class SlotsFull extends RuntimeException {
        private static final long serialVersionUID = 1L;

        SlotsFull() {
            super("All run slots on this instance are taken", null, false, false);
        }
    }

    private EventQueObject queObject(String appCode, String clientCode, String eventName, Map<String, Object> data,
            boolean draft) {
        return new EventQueObject()
                .setAppCode(appCode)
                .setClientCode(clientCode)
                .setEventName(eventName)
                .setDraft(draft)
                .setData(data);
    }

    /**
     * Runs claimed work in the background and frees its slot when it ends. The
     * runner closes the schedule_run / scheduled_event itself (trackCompletion),
     * the same as when the event comes off the queue.
     */
    private void runDetached(ScheduledEventRunner runner, EventQueObject qob, Runnable release) {
        Mono.defer(() -> runner.run(qob))
                .doFinally(s -> release.run())
                .subscribe(ok -> {
                }, e -> logger.warn("Scheduled run of {}.{} for {} failed: {}", qob.getAppCode(), qob.getEventName(),
                        qob.getClientCode(), e.getMessage()));
    }

    /**
     * Owner plus EDIT_ACCESS clients from security, reused for
     * {@code clientsRefreshSeconds}. When security cannot be reached the last
     * list keeps serving, or nobody runs if there never was one.
     */
    private Mono<Set<String>> writeAccessClients(String appCode, Instant now) {

        WriteAccessClients cached = this.writeAccess.get(appCode);
        if (cached != null && cached.fetchedAt().plusSeconds(this.settings.getClientsRefreshSeconds()).isAfter(now))
            return Mono.just(cached.codes());

        return this.securityService.getWriteAccessClientCodes(appCode)
                .<Set<String>>map(Set::copyOf)
                .doOnNext(codes -> this.writeAccess.put(appCode, new WriteAccessClients(codes, now)))
                .onErrorResume(e -> {
                    logger.warn("Scheduler could not read write-access clients of {}: {}", appCode, e.getMessage());
                    return Mono.just(cached == null ? Set.of() : cached.codes());
                })
                .defaultIfEmpty(Set.of());
    }

    /**
     * The codes among these that security says are not active: the client is deleted,
     * inactive or locked, or a client managing it is. Answers are reused for
     * {@code clientsRefreshSeconds} and fetched in one call for every code that is
     * stale. Only a client security has answered for is left out: when it cannot be
     * reached the last answers keep serving, and a client never answered for runs as it
     * did before this check existed.
     */
    private Mono<Set<String>> inactiveClients(Set<String> codes, Instant now) {

        if (codes.isEmpty())
            return Mono.just(Set.of());

        List<String> stale = codes.stream()
                .filter(code -> {
                    ClientState state = this.clientStates.get(code);
                    return state == null || !state.fetchedAt()
                            .plusSeconds(this.settings.getClientsRefreshSeconds())
                            .isAfter(now);
                })
                .sorted()
                .toList();

        Mono<Void> refresh = stale.isEmpty() ? Mono.empty()
                : Mono.defer(() -> this.securityService.getActiveClientCodes(stale))
                        .doOnNext(active -> {
                            Set<String> activeSet = Set.copyOf(active);
                            for (String code : stale)
                                this.clientStates.put(code, new ClientState(activeSet.contains(code), now));
                        })
                        .onErrorResume(e -> {
                            logger.warn("Scheduler could not read the status of {} clients: {}", stale.size(),
                                    e.getMessage());
                            return Mono.empty();
                        })
                        .then();

        return refresh.then(Mono.fromSupplier(() -> codes.stream()
                .filter(code -> {
                    ClientState state = this.clientStates.get(code);
                    return state != null && !state.active();
                })
                .collect(Collectors.toSet())));
    }

    /**
     * Closes a claimed one-off without raising it when its client is not active: FAILED
     * with the reason, and a line in the event run log, so it is not tried again. True
     * when it was closed, false when it should run.
     */
    private Mono<Boolean> refuseIfInactive(ScheduledEvent event, Instant now) {

        if (event.getClientCode() == null)
            return Mono.just(Boolean.FALSE);

        return this.inactiveClients(Set.of(event.getClientCode()), now)
                .flatMap(inactive -> {
                    if (!inactive.contains(event.getClientCode()))
                        return Mono.just(Boolean.FALSE);

                    String reason = "The client '" + event.getClientCode()
                            + "' is deleted, inactive or locked, so the event was not raised.";
                    return this.store.failScheduledEvent(event.getId(), event.getClaimToken(), null, reason, now)
                            .then(this.logRun(this.queObject(event.getAppCode(), event.getClientCode(),
                                    event.getEventName(), this.oneOffData(event), event.isDraft()),
                                    CoreEventRunsStatus.FAILED, reason))
                            .thenReturn(Boolean.TRUE);
                });
    }

    // ---------------------------------------------------------------- one-off

    private Mono<Void> dueOneOffs(Instant now, Counts counts) {

        ScheduledEventRunner runner = this.runner();
        if (runner != null)
            return this.claimOneOffs(runner, now, counts, Math.max(0, this.settings.getOneOffBatchSize()));

        return this.store.claimDueEvents(now, this.instanceId, this.settings.lease(),
                        this.settings.getOneOffBatchSize())
                .collectList()
                // One status call for the whole batch, so each raise below reads it from memory.
                .flatMapMany(list -> this.inactiveClients(list.stream().map(ScheduledEvent::getClientCode)
                        .filter(Objects::nonNull).collect(Collectors.toSet()), now)
                        .thenMany(Flux.fromIterable(list)))
                .flatMap(e -> this.raiseOneOff(e, now, counts), Math.max(1, this.settings.getConcurrency()))
                .then();
    }

    /**
     * In-process: claim one due one-off per free slot and start it, until the
     * slots or the due ones run out. Whatever is left stays PENDING for another
     * instance or the next pass.
     */
    private Mono<Void> claimOneOffs(ScheduledEventRunner runner, Instant now, Counts counts, int budget) {

        return Mono.defer(() -> {
            if (budget <= 0 || !this.tryTakeSlot())
                return Mono.<Void>empty();

            return this.store.claimDueEvent(now, this.instanceId, this.settings.lease())
                    .doOnError(e -> this.releaseSlot())
                    .switchIfEmpty(Mono.<ScheduledEvent>fromRunnable(this::releaseSlot))
                    .flatMap(e -> {
                        AtomicBoolean held = new AtomicBoolean(true);
                        Runnable release = () -> {
                            if (held.compareAndSet(true, false))
                                this.releaseSlot();
                        };
                        return this.startOneOff(runner, e, now, counts, release)
                                .then(this.claimOneOffs(runner, now, counts, budget - 1));
                    });
        });
    }

    private Mono<Void> startOneOff(ScheduledEventRunner runner, ScheduledEvent event, Instant now, Counts counts,
            Runnable release) {

        if (event.getAttempts() > event.getMaxAttempts()) {
            counts.oneOffsFailed.incrementAndGet();
            return this.store.failScheduledEvent(event.getId(), event.getClaimToken(), null,
                    "Lease ran out on the last attempt", now)
                    .then(this.logGaveUp(event))
                    .doFinally(s -> release.run())
                    .then();
        }

        return this.refuseIfInactive(event, now)
                .doOnError(e -> release.run())
                .flatMap(refused -> {
                    if (Boolean.TRUE.equals(refused)) {
                        counts.oneOffsFailed.incrementAndGet();
                        release.run();
                        return Mono.<Void>empty();
                    }

                    counts.oneOffsRaised.incrementAndGet();
                    this.runDetached(runner, this.queObject(event.getAppCode(), event.getClientCode(),
                            event.getEventName(), this.oneOffData(event), event.isDraft()), release);
                    return Mono.<Void>empty();
                });
    }

    private Map<String, Object> oneOffData(ScheduledEvent event) {

        Map<String, Object> marker = new HashMap<>();
        marker.put("kind", KIND_ONCE);
        marker.put("id", event.getId());
        marker.put("key", event.getKey());
        marker.put("claimToken", event.getClaimToken());
        marker.put("attempt", event.getAttempts());
        marker.put("runAt", event.getRunAt() == null ? null : event.getRunAt().toString());

        Map<String, Object> data = event.getData() == null ? new HashMap<>() : new HashMap<>(event.getData());
        data.put(SCHEDULE_DATA_KEY, marker);
        return data;
    }

    private Mono<Void> raiseOneOff(ScheduledEvent event, Instant now, Counts counts) {

        // A claim past the last attempt is one whose lease ran out on that last
        // attempt. It is not raised again.
        if (event.getAttempts() > event.getMaxAttempts()) {
            counts.oneOffsFailed.incrementAndGet();
            return this.store.failScheduledEvent(event.getId(), event.getClaimToken(), null,
                    "Lease ran out on the last attempt", now).then(this.logGaveUp(event));
        }

        Map<String, Object> data = this.oneOffData(event);

        return this.refuseIfInactive(event, now).flatMap(refused -> {

            if (Boolean.TRUE.equals(refused)) {
                counts.oneOffsFailed.incrementAndGet();
                return Mono.<Void>empty();
            }

            return this.raise(event.getAppCode(), event.getClientCode(), event.getEventName(), data, event.isDraft())
                    .doOnSuccess(v -> counts.oneOffsRaised.incrementAndGet())
                    .then()
                    .onErrorResume(e -> {
                        counts.oneOffsFailed.incrementAndGet();
                        return this.oneOffs.finish(event.getId(), event.getClaimToken(), false,
                                "Raise failed: " + e.getMessage())
                                .then(this.logRun(this.queObject(event.getAppCode(), event.getClientCode(),
                                        event.getEventName(), data, event.isDraft()), CoreEventRunsStatus.FAILED,
                                        "The scheduler could not raise the event: " + EventRunService.errorOf(e)));
                    });
        });
    }

    /** A one-off whose last attempt never reported back: it is not tried again, and the log says so. */
    private Mono<Void> logGaveUp(ScheduledEvent event) {
        return this.logRun(this.queObject(event.getAppCode(), event.getClientCode(), event.getEventName(),
                        this.oneOffData(event), event.isDraft()), CoreEventRunsStatus.FAILED,
                "Gave up after " + event.getMaxAttempts() + " attempts: the last attempt did not report back "
                        + "before its lease ran out, so it is not tried again.");
    }

    private Mono<Void> logRun(EventQueObject qob, CoreEventRunsStatus status, String error) {
        return this.eventRuns == null ? Mono.empty() : this.eventRuns.record(qob, status, error);
    }

    private Mono<Boolean> raise(String appCode, String clientCode, String eventName, Map<String, Object> data,
            boolean draft) {

        Mono<Boolean> raised = this.ecService.createEvent(new EventQueObject()
                .setAppCode(appCode)
                .setClientCode(clientCode)
                .setEventName(eventName)
                .setData(data))
                // createEvent publishes with a blocking AmqpTemplate.convertAndSend inside
                // Mono.fromCallable; keep it off the reactive driver (Mongo / Netty) threads.
                .subscribeOn(Schedulers.boundedElastic());

        // EventCreationService takes the draft marker from the context at publish
        // time, the same way it does for a request on the draft surface.
        return draft ? raised.contextWrite(Context.of(LogUtil.DRAFT_KEY, Boolean.TRUE)) : raised;
    }

    // ---------------------------------------------------------------- completion

    /**
     * Wraps EventsQueListener's processing of one event so that a scheduled run
     * is closed with the outcome of its tasks. An event that did not come from
     * the scheduler passes through untouched.
     *
     * <p>
     * No EventAction at all counts as done: there was nothing to run. The
     * listener turns a task's error into false, so false is a failure, and an
     * error escaping the listener is a failure too, rethrown after recording.
     */
    public Mono<Boolean> trackCompletion(EventQueObject qob, Mono<Boolean> processing) {

        if (scheduleMarker(qob) == null)
            return processing;

        return processing
                .defaultIfEmpty(Boolean.TRUE)
                .onErrorResume(e -> this.complete(qob, false, e.getMessage()).then(Mono.error(e)))
                .flatMap(ok -> this.complete(qob, Boolean.TRUE.equals(ok), Boolean.TRUE.equals(ok) ? null
                        : "The EventAction reported a failure").thenReturn(ok));
    }

    private Mono<Boolean> complete(EventQueObject qob, boolean success, String error) {

        Map<?, ?> marker = scheduleMarker(qob);
        if (marker == null)
            return Mono.just(Boolean.FALSE);

        Object kind = marker.get("kind");
        Mono<Boolean> done;

        if (KIND_CRON.equals(kind) && marker.get("runId") instanceof String runId)
            done = this.store.finishRun(runId, success ? ScheduleRun.Status.DONE : ScheduleRun.Status.FAILED,
                    error, this.clock.instant());
        else if (KIND_ONCE.equals(kind) && marker.get("id") instanceof String id
                && marker.get("claimToken") instanceof String token)
            done = this.oneOffs.finish(id, token, success, error);
        else
            done = Mono.just(Boolean.FALSE);

        return done.onErrorResume(e -> {
            logger.warn("Scheduler could not close the run of {}.{} for {}: {}", qob.getAppCode(),
                    qob.getEventName(), qob.getClientCode(), e.getMessage());
            return Mono.just(Boolean.FALSE);
        });
    }

    private static Map<?, ?> scheduleMarker(EventQueObject qob) {
        if (qob == null || qob.getData() == null)
            return null;
        return qob.getData().get(SCHEDULE_DATA_KEY) instanceof Map<?, ?> m ? m : null;
    }
}
