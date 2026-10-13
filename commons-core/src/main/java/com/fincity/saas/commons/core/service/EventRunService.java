package com.fincity.saas.commons.core.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.dao.EventRunDAO;
import com.fincity.saas.commons.core.dto.EventRun;
import com.fincity.saas.commons.core.jooq.enums.CoreEventRunsStatus;
import com.fincity.saas.commons.core.jooq.enums.CoreEventRunsTriggerType;
import com.fincity.saas.commons.core.model.EventRunTask;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * The event run log: one core_event_runs row per run of an event's EventAction,
 * and per outcome that kept one from running.
 *
 * <p>
 * <b>Writing never fails the run.</b> Every write here swallows its own error
 * (logged), because a log that can stop the thing it logs is worse than a gap in
 * the log.
 *
 * <p>
 * <b>Reading is per client.</b> A caller holding {@code Authorities.EventAction_READ}
 * reads its own client's runs, and a client it manages when its own client has
 * read access to the app: the same rule AbstractOverridableDataService applies to
 * reading another client's definitions.
 */
@Service
public class EventRunService {

    private static final Logger logger = LoggerFactory.getLogger(EventRunService.class);

    /** The key in an event's data that says which scheduled run it belongs to (EventScheduler.SCHEDULE_DATA_KEY). */
    public static final String SCHEDULE_DATA_KEY = "_schedule";

    /**
     * Events another service hands to core so an app can own a channel. Losing one
     * loses a customer's message or call, so an unhandled one is always recorded.
     */
    public static final Set<String> HANDOFF_EVENTS = Set.of("WHATSAPP_INBOUND", "CALL_EVENT");

    private static final int MAX_ERROR_LENGTH = 4000;

    private static final int DELETE_BATCH = 5000;

    /**
     * The sweep's floors and cap. The internal endpoint that drives it is
     * unauthenticated (the worker calls core directly through Eureka), so what keeps
     * it safe is that no caller can ask for more than this: nothing younger than
     * {@link #MIN_RETENTION_DAYS} is ever deleted, a run is never called stale before
     * {@link #MIN_STALE_HOURS}, and one call removes at most {@link #MAX_CLEANUP_LIMIT}
     * rows. The worst an arbitrary caller achieves is running the sweep early.
     */
    public static final int MIN_RETENTION_DAYS = 30;
    public static final int MIN_SUCCESS_RETENTION_DAYS = 1;
    public static final int MIN_STALE_HOURS = 1;
    public static final int MAX_CLEANUP_LIMIT = 200_000;

    /**
     * A NO_ACTION row is written at most once per app, client and event in this
     * window, per instance. The first says what is wrong; the next thousand say the
     * same thing, and a bulk import into a storage whose event has no action would
     * otherwise write one per row. The row after a window carries how many were not
     * written, so the count is not lost. Hand-offs are exempt: each one is a lost
     * customer message or call, and is recorded every time.
     */
    static final long NO_ACTION_WINDOW_MS = 60L * 60L * 1000L;

    /** Bound on the remembered keys, so an unbounded set of event names cannot grow the map. */
    static final int MAX_NO_ACTION_KEYS = 10_000;

    private final EventRunDAO dao;
    private final EventDefinitionService edService;
    private final IFeignSecurityService securityService;
    private final CoreMessageResourceService messageService;

    private final String instanceId;

    /** Per app|client|event: [start of the current window, occurrences not written in it]. */
    private final Map<String, long[]> noActionWindows = new ConcurrentHashMap<>();

    /** Off stops writing the log; reading and the worker's sweep still work. */
    @Value("${core.eventRuns.enabled:true}")
    private boolean enabled = true;

    public EventRunService(EventRunDAO dao, EventDefinitionService edService, IFeignSecurityService securityService,
            CoreMessageResourceService messageService) {
        this.dao = dao;
        this.edService = edService;
        this.securityService = securityService;
        this.messageService = messageService;

        String host = System.getenv("HOSTNAME");
        this.instanceId = host == null || host.isBlank() ? "core" : host;
    }

    // ---------------------------------------------------------------- writing

    /** Opens a RUNNING row for an event about to run its action. Empty Optional when it could not be written. */
    public Mono<Optional<ULong>> start(EventQueObject qob, int taskCount) {

        if (!this.enabled || qob == null)
            return Mono.just(Optional.empty());

        EventRun run = describe(qob)
                .setStatus(CoreEventRunsStatus.RUNNING)
                .setTaskCount(taskCount)
                .setInstanceId(this.instanceId)
                .setStartedAt(now());

        return this.dao.insert(run)
                .map(Optional::of)
                .onErrorResume(e -> {
                    logger.warn("Could not open the run log row of {}.{} for {}: {}", qob.getAppCode(),
                            qob.getEventName(), qob.getClientCode(), e.getMessage());
                    return Mono.just(Optional.empty());
                })
                .defaultIfEmpty(Optional.empty());
    }

    /** Closes the row {@link #start} opened with the run's outcome. */
    public Mono<Void> finish(Optional<ULong> id, long startedAtMillis, boolean success, List<EventRunTask> tasks,
            String error) {

        if (id == null || id.isEmpty())
            return Mono.empty();

        int failed = tasks == null ? 0
                : (int) tasks.stream().filter(t -> t.getStatus() == EventRunTask.Status.FAILED).count();

        String finalError = error;
        if (finalError == null && failed > 0)
            finalError = failed == 1 ? "1 task failed" : failed + " tasks failed";

        return this.dao.finish(id.get(), success ? CoreEventRunsStatus.SUCCESS : CoreEventRunsStatus.FAILED,
                        tasks, failed, truncate(finalError), now(), System.currentTimeMillis() - startedAtMillis)
                .onErrorResume(e -> {
                    logger.warn("Could not close run log row {}: {}", id.get(), e.getMessage());
                    return Mono.just(false);
                })
                .then();
    }

    /**
     * Records an outcome that never ran an action: SKIPPED, a raise that FAILED, a
     * NO_ACTION. One complete row.
     */
    public Mono<Void> record(EventQueObject qob, CoreEventRunsStatus status, String error) {

        if (!this.enabled || qob == null)
            return Mono.empty();

        LocalDateTime now = now();
        EventRun run = describe(qob)
                .setStatus(status)
                .setError(truncate(error))
                .setInstanceId(this.instanceId)
                .setStartedAt(now)
                .setFinishedAt(now)
                .setDurationMs(0L);

        return this.dao.insert(run)
                .onErrorResume(e -> {
                    logger.warn("Could not record {} run of {}.{} for {}: {}", status, qob.getAppCode(),
                            qob.getEventName(), qob.getClientCode(), e.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    /**
     * An event arrived with no EventAction of its name. Recorded only when someone
     * evidently meant it to run: it was scheduled, it is a hand-off, or the app
     * defines it. A platform event such as USER_REGISTERED that an app simply does
     * not react to is not a failed run, and would otherwise be most of the table.
     *
     * <p>
     * Written once per app, client and event an hour; see {@link #NO_ACTION_WINDOW_MS}.
     */
    public Mono<Void> recordNoAction(EventQueObject qob) {
        return this.recordNoAction(qob, System.currentTimeMillis());
    }

    Mono<Void> recordNoAction(EventQueObject qob, long now) {

        if (!this.enabled || qob == null)
            return Mono.empty();

        String error = "No Event action named '" + qob.getEventName() + "' exists for this app and client, so "
                + "nothing ran. Create one with this exact name.";

        if (HANDOFF_EVENTS.contains(qob.getEventName()))
            return this.record(qob, CoreEventRunsStatus.NO_ACTION, error);

        if (scheduleMarker(qob) != null)
            return this.recordThrottled(qob, error, now);

        return this.edService.readEffective(qob.getEventName(), qob.getAppCode(), qob.getClientCode())
                .flatMap(ed -> this.recordThrottled(qob, error, now).thenReturn(true))
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    private Mono<Void> recordThrottled(EventQueObject qob, String error, long now) {

        String key = qob.getAppCode() + "|" + qob.getClientCode() + "|" + qob.getEventName();

        if (this.noActionWindows.size() >= MAX_NO_ACTION_KEYS && !this.noActionWindows.containsKey(key))
            this.noActionWindows.clear();

        // [0] is -1 when this occurrence is suppressed, else how many the window before it suppressed.
        long[] outcome = new long[1];
        this.noActionWindows.compute(key, (k, w) -> {
            if (w != null && now - w[0] < NO_ACTION_WINDOW_MS) {
                w[1]++;
                outcome[0] = -1;
                return w;
            }
            outcome[0] = w == null ? 0 : w[1];
            return new long[] { now, 0 };
        });

        if (outcome[0] < 0)
            return Mono.empty();

        String text = error + " Recorded once an hour; later occurrences in the hour are counted, not listed.";
        if (outcome[0] > 0)
            text += " It also happened " + outcome[0] + (outcome[0] == 1 ? " more time" : " more times")
                    + " in the hour before this.";

        return this.record(qob, CoreEventRunsStatus.NO_ACTION, text);
    }

    /** The parts of a row that come from the event itself. */
    static EventRun describe(EventQueObject qob) {

        EventRun run = new EventRun()
                .setAppCode(qob.getAppCode())
                .setClientCode(qob.getClientCode())
                .setEventName(qob.getEventName())
                .setIsDraft(qob.isDraft())
                .setTriggerType(CoreEventRunsTriggerType.EVENT);

        ContextAuthentication ca = qob.getAuthentication();
        if (ca != null && ca.getUser() != null && ca.getUser().getId() != null)
            run.setTriggeredBy(ULong.valueOf(ca.getUser().getId()));

        Map<?, ?> marker = scheduleMarker(qob);
        if (marker == null)
            return run;

        if ("cron".equals(marker.get("kind"))) {
            run.setTriggerType(CoreEventRunsTriggerType.SCHEDULE)
                    .setScheduleRunId(stringOf(marker.get("runId")))
                    .setScheduledFor(timeOf(marker.get("tickAt")));
        } else if ("once".equals(marker.get("kind"))) {
            run.setTriggerType(CoreEventRunsTriggerType.ONE_OFF)
                    .setScheduleRunId(stringOf(marker.get("id")))
                    .setScheduledFor(timeOf(marker.get("runAt")))
                    .setAttempt(marker.get("attempt") instanceof Number n ? n.intValue() : null);
        }

        return run;
    }

    public static Map<?, ?> scheduleMarker(EventQueObject qob) {
        if (qob == null || qob.getData() == null)
            return null;
        return qob.getData().get(SCHEDULE_DATA_KEY) instanceof Map<?, ?> m ? m : null;
    }

    /** What a user should read for an exception: its message, and the root cause's when that says more. */
    public static String errorOf(Throwable t) {

        if (t == null)
            return null;

        String msg = describe(t);

        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root)
            root = root.getCause();

        if (root != t) {
            String rootText = describe(root);
            if (!msg.contains(rootText))
                msg = msg + " (cause: " + rootText + ")";
        }

        List<String> steps = kirunSteps(t);
        if (!steps.isEmpty())
            msg = msg + ", " + steps.getFirst().lines().findFirst().orElse("");

        return truncate(msg);
    }

    /**
     * kirun-java (4.9.2 on) adds a KIRuntimeStepTrace to a step's error as a suppressed
     * exception: the step, the function it is in, and the step's arguments. Its message is the
     * location on the first line and the arguments on the second. Matched by class name so this
     * builds against kirun versions without the class.
     */
    static final String KIRUN_STEP_TRACE = "KIRuntimeStepTrace";

    /** Shown in full in a detail; the steps further out, the callers, show only where they are. */
    static final int MAX_DETAIL_STEPS_WITH_ARGUMENTS = 3;

    /** The KIRun step traces on an exception and its causes, innermost step first. */
    static List<String> kirunSteps(Throwable t) {

        List<String> steps = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        for (Throwable cur = t; cur != null && seen.add(cur); cur = cur.getCause())
            for (Throwable s : cur.getSuppressed())
                if (KIRUN_STEP_TRACE.equals(s.getClass().getSimpleName()) &&!StringUtil.safeIsBlank(s.getMessage()))
                    steps.add(s.getMessage());

        return steps;
    }

    /**
     * An exception's message, or for one without a message (a ConcurrentModificationException,
     * a NullPointerException) its class and the first frame outside the JDK and Reactor, which
     * is the code that broke.
     */
    private static String describe(Throwable t) {

        if (!StringUtil.safeIsBlank(t.getMessage()))
            return t.getMessage();

        String text = t.getClass().getSimpleName();
        for (StackTraceElement f : t.getStackTrace()) {
            if (isLibraryFrame(f))
                continue;
            String cls = f.getClassName();
            return text + " in " + cls.substring(cls.lastIndexOf('.') + 1) + "." + f.getMethodName()
                    + (f.getLineNumber() > 0 ? ":" + f.getLineNumber() : "");
        }
        return text;
    }

    /** Room for three steps' arguments (kirun bounds each at 2000) and the stack. */
    static final int MAX_DETAIL_LENGTH = 12000;
    static final int MAX_DETAIL_FRAMES = 25;
    static final int MAX_DETAIL_CAUSES = 5;

    /**
     * What whoever fixes it needs: the KIRun steps the error came out of with the arguments they
     * got, when it came from a KIRun function, then the exception and each cause, with its class,
     * message and frames. Runs of Reactor, Netty and reflection frames, most of a reactive trace,
     * fold into a count. Null for no exception.
     */
    public static String detailOf(Throwable t) {

        if (t == null)
            return null;

        StringBuilder sb = new StringBuilder();
        appendSteps(sb, kirunSteps(t));

        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        Throwable cur = t;
        for (int depth = 0; cur != null && depth < MAX_DETAIL_CAUSES && seen.add(cur); depth++) {

            if (depth > 0)
                sb.append("Caused by: ");
            sb.append(cur.getClass().getName());
            if (!StringUtil.safeIsBlank(cur.getMessage()))
                sb.append(": ").append(cur.getMessage());
            sb.append('\n');

            appendFrames(sb, cur.getStackTrace());
            cur = cur.getCause();
        }

        if (sb.length() <= MAX_DETAIL_LENGTH)
            return sb.toString();
        return sb.substring(0, MAX_DETAIL_LENGTH - 1) + "…";
    }

    private static void appendSteps(StringBuilder sb, List<String> steps) {

        if (steps.isEmpty())
            return;

        sb.append("KIRun steps, innermost first:\n");
        for (int i = 0; i < steps.size(); i++) {
            List<String> lines = steps.get(i).lines().toList();
            sb.append("    ").append(lines.getFirst()).append('\n');
            if (i < MAX_DETAIL_STEPS_WITH_ARGUMENTS)
                for (String line : lines.subList(1, lines.size()))
                    sb.append("        ").append(line).append('\n');
        }
        sb.append('\n');
    }

    private static void appendFrames(StringBuilder sb, StackTraceElement[] frames) {

        int shown = 0;
        int folded = 0;
        int i = 0;
        for (; i < frames.length && shown < MAX_DETAIL_FRAMES; i++) {
            if (isPlumbingFrame(frames[i])) {
                folded++;
            } else {
                if (folded > 0)
                    sb.append("    ... ").append(folded).append(" framework frames\n");
                folded = 0;
                sb.append("    at ").append(frameText(frames[i])).append('\n');
                shown++;
            }
        }

        int rest = folded + frames.length - i;
        if (rest > 0)
            sb.append("    ... ").append(rest).append(" more\n");
    }

    private static String frameText(StackTraceElement f) {
        String where = f.getFileName() == null ? "Unknown Source" : f.getFileName();
        if (f.getFileName() != null && f.getLineNumber() > 0)
            where += ":" + f.getLineNumber();
        return f.getClassName() + "." + f.getMethodName() + "(" + where + ")";
    }

    /** Frames that only carry the call along: Reactor's operators, Netty, reflection. */
    private static boolean isPlumbingFrame(StackTraceElement f) {
        String c = f.getClassName();
        return c.startsWith("reactor.") || c.startsWith("io.netty.") || c.startsWith("java.lang.reflect.")
                || c.startsWith("jdk.internal.") || c.startsWith("sun.reflect.");
    }

    /** Frames of code that is not ours: the JDK and the plumbing. */
    private static boolean isLibraryFrame(StackTraceElement f) {
        String c = f.getClassName();
        return isPlumbingFrame(f) || c.startsWith("java.") || c.startsWith("javax.") || c.startsWith("sun.")
                || c.startsWith("com.google.gson.");
    }

    // ---------------------------------------------------------------- reading

    /** Most clients "all clients" will consider at once. Past this the oldest-named are left out. */
    static final int MAX_CANDIDATE_CLIENTS = 5000;

    /**
     * Runs of {@code appCode}, newest first: for {@code clientCode} (the caller's
     * own client when blank), or with {@code allClients} for every client the
     * caller may read that has a run of this event.
     */
    @PreAuthorize("hasAuthority('Authorities.EventAction_READ')")
    public Mono<Page<EventRun>> readPage(String appCode, String clientCode, boolean allClients, String eventName,
            CoreEventRunsStatus status, CoreEventRunsTriggerType triggerType, LocalDateTime from, LocalDateTime to,
            Pageable pageable) {
        return this.readPage(appCode, clientCode, allClients, eventName, status, triggerType, from, to, pageable,
                null);
    }

    /** As above, with display text for each run in {@code zone} when it is given. */
    @PreAuthorize("hasAuthority('Authorities.EventAction_READ')")
    public Mono<Page<EventRun>> readPage(String appCode, String clientCode, boolean allClients, String eventName,
            CoreEventRunsStatus status, CoreEventRunsTriggerType triggerType, LocalDateTime from, LocalDateTime to,
            Pageable pageable, String zone) {

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> allClients
                                ? this.requireApp(appCode)
                                        .then(this.dao.distinctClients(appCode, eventName, MAX_CANDIDATE_CLIENTS))
                                        .flatMap(candidates -> this.visibleAmong(ca, appCode, candidates, true))
                                : this.allowedClient(ca, appCode, clientCode).map(List::of),

                        (ca, clients) -> this.dao.readPage(appCode, clients, eventName, status, triggerType, from,
                                to, pageable))
                .map(page -> {
                    if (!StringUtil.safeIsBlank(zone))
                        page.getContent().forEach(r -> decorate(r, zone));
                    return page;
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "EventRunService.readPage"));
    }

    /**
     * Of {@code candidates}, the clients {@code ca} may read for {@code appCode}:
     * its own, and those it manages when its own client may read the app. One
     * batch call to security however many candidates there are. With
     * {@code includeOwn} the caller's own client is in the answer even when it is
     * not a candidate.
     */
    public Mono<List<String>> visibleAmong(ContextAuthentication ca, String appCode, Collection<String> candidates,
            boolean includeOwn) {

        String own = ca.getClientCode();
        List<String> others = candidates == null ? List.of()
                : candidates.stream().filter(c -> c != null && !c.equals(own)).distinct().toList();
        boolean ownIn = includeOwn || (candidates != null && candidates.contains(own));

        Mono<List<String>> managed = others.isEmpty() ? Mono.just(List.of())
                : this.securityService.hasReadAccess(appCode, own)
                        .flatMap(access -> Boolean.TRUE.equals(access)
                                ? this.securityService.filterManagedClientCodes(own, others)
                                : Mono.just(List.<String>of()))
                        .defaultIfEmpty(List.of());

        return managed.map(m -> {
            List<String> out = new ArrayList<>();
            if (ownIn)
                out.add(own);
            m.stream().filter(c -> !c.equals(own)).forEach(out::add);
            return out;
        });
    }

    /** The newest run of this event per client, keyed by client code. No access check: callers do their own. */
    public Mono<Map<String, EventRun>> latestRuns(String appCode, String eventName, List<String> clientCodes) {

        if (clientCodes == null || clientCodes.isEmpty())
            return Mono.just(Map.of());

        return this.dao.latestPerClient(appCode, eventName, clientCodes)
                .map(runs -> {
                    Map<String, EventRun> byClient = new HashMap<>();
                    runs.forEach(r -> byClient.put(r.getClientCode(), r));
                    return byClient;
                })
                .onErrorResume(e -> {
                    logger.warn("Could not read the latest runs of {}.{}: {}", appCode, eventName, e.getMessage());
                    return Mono.just(Map.of());
                });
    }

    private Mono<Boolean> requireApp(String appCode) {
        return StringUtil.safeIsBlank(appCode)
                ? this.messageService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        AbstractMongoMessageResourceService.APPLICATION_NAME_REQUIRED)
                : Mono.just(true);
    }

    @PreAuthorize("hasAuthority('Authorities.EventAction_READ')")
    public Mono<EventRun> read(ULong id) {

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> this.dao.read(id),

                        (ca, run) -> this.allowedClient(ca, run.getAppCode(), run.getClientCode()),

                        (ca, run, client) -> Mono.just(run))
                .switchIfEmpty(Mono.defer(() -> this.messageService.throwMessage(
                        msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
                        AbstractMongoMessageResourceService.OBJECT_NOT_FOUND, "Event run", id)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "EventRunService.read"));
    }

    /**
     * The client whose runs the caller may read: its own, or one it manages when
     * its own client may read the app. Anything else is a 403, never an empty page,
     * so a mistyped client code is not mistaken for "never ran".
     */
    private Mono<String> allowedClient(ContextAuthentication ca, String appCode, String clientCode) {

        if (StringUtil.safeIsBlank(appCode))
            return this.messageService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    AbstractMongoMessageResourceService.APPLICATION_NAME_REQUIRED);

        String client = StringUtil.safeIsBlank(clientCode) ? ca.getClientCode() : clientCode.trim();

        if (client.equals(ca.getClientCode()))
            return Mono.just(client);

        return FlatMapUtil.flatMapMono(
                        () -> this.securityService.doesClientManageClientCode(ca.getClientCode(), client),
                        managed -> Boolean.TRUE.equals(managed)
                                ? this.securityService.hasReadAccess(appCode, ca.getClientCode())
                                : Mono.just(false),
                        (managed, appAccess) -> Boolean.TRUE.equals(appAccess) ? Mono.just(client) : Mono.empty())
                .switchIfEmpty(Mono.defer(() -> this.messageService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        CoreMessageResourceService.FORBIDDEN_CLIENT_CODE, client)));
    }

    // ---------------------------------------------------------------- cleanup

    /**
     * Closes RUNNING rows with no outcome after {@code staleAfterHours} as FAILED,
     * deletes SUCCESS rows started more than {@code successRetentionDays} ago, then
     * every row started more than {@code retentionDays} ago: at most {@code limit}
     * rows in all, in batches so no single delete holds a long lock.
     *
     * <p>
     * Successes go sooner because they are nearly every row and the least looked
     * at: a minute-by-minute schedule is 1,440 of them a client a day. A failure,
     * a skip or a missing action is what someone comes looking for, so those keep
     * the full retention.
     *
     * <p>
     * Driven by the worker's EVENT_RUN_CLEANUP task, not a {@code @Scheduled} here:
     * production runs several core instances, and a scheduled method fires on all
     * of them at once. The worker's Quartz cluster runs it once, somewhere, and its
     * task row is where the schedule and the retention are changed.
     *
     * <p>
     * Answers {@code runsRemoved} (all deleted), {@code successesRemoved} (of those,
     * by the shorter success retention) and {@code staleClosed}, which the worker
     * writes to the task's LAST_FIRE_RESULT. A {@code runsRemoved} equal to the limit
     * means there was more to delete: raise the limit or run it more often.
     */
    public Mono<Map<String, Integer>> cleanupOlderThan(int retentionDays, int successRetentionDays,
            int staleAfterHours, int limit) {

        int days = Math.max(retentionDays, MIN_RETENTION_DAYS);
        int successDays = Math.clamp(successRetentionDays, MIN_SUCCESS_RETENTION_DAYS, days);
        int stale = Math.max(staleAfterHours, MIN_STALE_HOURS);
        int cap = Math.clamp(limit, 1, MAX_CLEANUP_LIMIT);

        LocalDateTime now = now();

        return this.dao.failRunningStartedBefore(now.minusHours(stale),
                        "No outcome was reported for this run. The server running it may have stopped part way "
                                + "through; check whether its work was done.",
                        now)
                .onErrorResume(e -> {
                    logger.warn("Event run log: could not close stale runs: {}", e.getMessage());
                    return Mono.just(0);
                })
                .flatMap(closed -> this.deleteBatches(now.minusDays(successDays), CoreEventRunsStatus.SUCCESS,
                                cap, 0)
                        .flatMap(successes -> this.deleteBatches(now.minusDays(days), null, cap, successes)
                                .map(removed -> Map.of("runsRemoved", removed, "successesRemoved", successes,
                                        "staleClosed", closed))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "EventRunService.cleanupOlderThan"));
    }

    /** Deletes in batches until a batch comes back short or {@code cap} rows in all are gone. */
    private Mono<Integer> deleteBatches(LocalDateTime before, CoreEventRunsStatus status, int cap,
            int deletedSoFar) {

        int batch = Math.min(DELETE_BATCH, cap - deletedSoFar);
        if (batch <= 0)
            return Mono.just(deletedSoFar);

        return this.dao.deleteStartedBefore(before, status, batch)
                .flatMap(n -> n >= batch ? this.deleteBatches(before, status, cap, deletedSoFar + n)
                        : Mono.just(deletedSoFar + n));
    }

    // ---------------------------------------------------------------- helpers

    private static final java.time.format.DateTimeFormatter RUN_TIME = java.time.format.DateTimeFormatter
            .ofPattern("EEE d MMM, HH:mm:ss", java.util.Locale.ENGLISH);

    /** Fills a run's display text in {@code zone} (UTC when it cannot be read). */
    public static EventRun decorate(EventRun r, String zone) {

        if (r == null)
            return null;

        java.time.ZoneId z;
        try {
            z = java.time.ZoneId.of(zone == null || zone.isBlank() ? "UTC" : zone.trim());
        } catch (RuntimeException e) {
            z = ZoneOffset.UTC;
        }

        if (r.getStartedAt() != null)
            r.setStartedText(r.getStartedAt().atOffset(ZoneOffset.UTC).atZoneSameInstant(z).format(RUN_TIME));
        if (r.getScheduledFor() != null)
            r.setScheduledForText(r.getScheduledFor().atOffset(ZoneOffset.UTC).atZoneSameInstant(z).format(RUN_TIME));
        if (r.getDurationMs() != null)
            r.setDurationText(duration(r.getDurationMs()));
        if (r.getTasks() != null)
            r.getTasks().forEach(t -> t.setDurationText(t.getDurationMs() == null ? null : duration(t.getDurationMs())));
        return r;
    }

    static String duration(long ms) {
        if (ms < 1000)
            return ms + " ms";
        if (ms < 60_000)
            return String.format(java.util.Locale.ENGLISH, "%.1f s", ms / 1000.0);
        return (ms / 60_000) + " min " + ((ms % 60_000) / 1000) + " s";
    }

    static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static String stringOf(Object o) {
        return o == null ? null : o.toString();
    }

    private static LocalDateTime timeOf(Object o) {
        if (o == null)
            return null;
        try {
            return LocalDateTime.ofInstant(Instant.parse(o.toString()), ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String truncate(String s) {
        if (s == null || s.length() <= MAX_ERROR_LENGTH)
            return s;
        return s.substring(0, MAX_ERROR_LENGTH - 1) + "…";
    }
}
