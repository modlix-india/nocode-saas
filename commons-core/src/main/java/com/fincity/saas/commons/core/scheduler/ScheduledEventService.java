package com.fincity.saas.commons.core.scheduler;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fincity.saas.commons.util.LogUtil;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * The one-off half of the scheduler: what CoreServices.Event.ScheduleEvent and
 * CancelScheduledEvent write, and how a claimed one-off is closed when its
 * EventAction reports back.
 *
 * <p>
 * Raising the due ones is {@link EventScheduler}'s job, in the same pass as the
 * recurring ticks.
 */
@Service
public class ScheduledEventService {

    private final ScheduleStore store;
    private final EventSchedulerSettings settings;

    private Clock clock = Clock.systemUTC();

    public ScheduledEventService(ScheduleStore store, EventSchedulerSettings settings) {
        this.store = store;
        this.settings = settings;
    }

    /** Tests only. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    /** What a schedule request resolved to: the key it was stored under, or empty when it was refused. */
    public record Scheduled(String key, ScheduledEvent event) {
    }

    /**
     * Stores the one-off under {@code key}, replacing a pending, finished or
     * cancelled row with the same key. A blank key gets a fresh one, which is
     * returned so the caller can cancel it later.
     *
     * <p>
     * Answers with a null event when a row with this key is claimed right now:
     * that run is in flight and is left alone rather than replaced under it.
     */
    public Mono<Scheduled> schedule(String appCode, String clientCode, boolean draft, String eventName,
            Map<String, Object> data, Instant runAt, String key, String scheduledBy) {

        String finalKey = key == null || key.isBlank() ? UUID.randomUUID().toString() : key;
        Instant now = this.clock.instant();

        ScheduledEvent event = new ScheduledEvent()
                .setAppCode(appCode)
                .setClientCode(clientCode)
                .setDraft(draft)
                .setKey(finalKey)
                .setEventName(eventName)
                .setData(data == null ? new HashMap<>() : new HashMap<>(data))
                .setRunAt(runAt == null ? now : runAt)
                .setMaxAttempts(Math.max(1, this.settings.getOneOffMaxAttempts()))
                .setScheduledBy(scheduledBy);

        return this.store.ensureIndexes()
                .then(this.store.upsertScheduledEvent(event, now))
                .map(e -> new Scheduled(finalKey, e))
                .defaultIfEmpty(new Scheduled(finalKey, null))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ScheduledEventService.schedule"));
    }

    /** True when a PENDING row with this key was cancelled. */
    public Mono<Boolean> cancel(String appCode, String clientCode, boolean draft, String key) {

        if (key == null || key.isBlank())
            return Mono.just(Boolean.FALSE);

        return this.store.ensureIndexes()
                .then(this.store.cancelScheduledEvent(appCode, clientCode, draft, key, this.clock.instant()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ScheduledEventService.cancel"));
    }

    /**
     * Closes a claim. Success is DONE. A failure goes back to PENDING after a
     * backoff that doubles per attempt, until the attempts are spent, then FAILED.
     * Only the claim named by {@code claimToken} can be closed, so a report that
     * arrives after the lease ran out and someone else took the row is ignored.
     */
    public Mono<Boolean> finish(String id, String claimToken, boolean success, String error) {

        Instant now = this.clock.instant();

        if (success)
            return this.store.completeScheduledEvent(id, claimToken, now);

        return this.store.findScheduledEvent(id)
                .filter(e -> claimToken != null && claimToken.equals(e.getClaimToken()))
                .flatMap(e -> {
                    Instant retryAt = e.getAttempts() >= e.getMaxAttempts() ? null
                            : now.plus(this.settings.oneOffBackoff(e.getAttempts()));
                    return this.store.failScheduledEvent(id, claimToken, retryAt, error, now);
                })
                .defaultIfEmpty(Boolean.FALSE)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ScheduledEventService.finish"));
    }
}
