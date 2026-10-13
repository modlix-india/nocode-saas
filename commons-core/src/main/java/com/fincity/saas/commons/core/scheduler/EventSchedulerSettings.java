package com.fincity.saas.commons.core.scheduler;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Knobs for {@link EventScheduler} and {@link ScheduledEventService}. Setters
 * exist for tests; at runtime everything comes from configuration.
 */
@Data
@Accessors(chain = true)
@Component
public class EventSchedulerSettings {

    /** Off stops both the recurring and the one-off sweeps on this instance. */
    @Value("${core.scheduler.enabled:true}")
    private boolean enabled = true;

    /** How often a pass runs. The @Scheduled trigger reads the same property. */
    @Value("${core.scheduler.tickMs:30000}")
    private long tickMs = 30000;

    /** How long a claimed run or one-off may go without finishing before it can be retaken. */
    @Value("${core.scheduler.leaseSeconds:300}")
    private long leaseSeconds = 300;

    /**
     * How far back a missed tick is still caught up. Only the latest missed tick
     * ever runs, and only if a run was recorded before it.
     */
    @Value("${core.scheduler.catchUpWindowMinutes:1500}")
    private long catchUpWindowMinutes = 1500;

    /** How long the app's write-access client list is reused before asking security again. */
    @Value("${core.scheduler.clientsRefreshSeconds:300}")
    private long clientsRefreshSeconds = 300;

    /** schedule_run rows expire this long after they are created. Must exceed the catch-up window. */
    @Value("${core.scheduler.runRetentionDays:3}")
    private long runRetentionDays = 3;

    /** Clients worked on at once within one pass (reading definitions, claiming). */
    @Value("${core.scheduler.concurrency:16}")
    private int concurrency = 16;

    /**
     * Scheduled runs (ticks and one-offs) this instance executes at once. An
     * instance claims a run only while it has a free slot, so with several
     * instances the work spreads by what each can take: a busy or slow instance
     * claims less and the others pick up the rest.
     */
    @Value("${core.scheduler.runConcurrency:4}")
    private int runConcurrency = 4;

    @Value("${core.scheduler.oneoff.batchSize:200}")
    private int oneOffBatchSize = 200;

    @Value("${core.scheduler.oneoff.maxAttempts:5}")
    private int oneOffMaxAttempts = 5;

    @Value("${core.scheduler.oneoff.backoffBaseSeconds:30}")
    private long oneOffBackoffBaseSeconds = 30;

    @Value("${core.scheduler.oneoff.backoffMaxSeconds:3600}")
    private long oneOffBackoffMaxSeconds = 3600;

    /** Finished one-offs (DONE, FAILED, CANCELLED) expire this long after they finish. */
    @Value("${core.scheduler.oneoff.retentionDays:7}")
    private long oneOffRetentionDays = 7;

    public Duration lease() {
        return Duration.ofSeconds(this.leaseSeconds);
    }

    public Duration catchUpWindow() {
        return Duration.ofMinutes(this.catchUpWindowMinutes);
    }

    /**
     * A tick younger than this is the one due right now rather than a missed
     * one. Three passes wide, so a pass delayed by a slow neighbour or a GC
     * pause still treats it as current.
     */
    public Duration freshWindow() {
        return Duration.ofMillis(Math.max(3 * this.tickMs, 60_000));
    }

    /** Wait before attempt {@code attempts + 1}: base doubled per attempt made, capped. */
    public Duration oneOffBackoff(int attempts) {
        long base = Math.max(1, this.oneOffBackoffBaseSeconds);
        int shift = Math.max(0, Math.min(attempts - 1, 30));
        long seconds = base << shift;
        if (seconds <= 0 || seconds > this.oneOffBackoffMaxSeconds)
            seconds = this.oneOffBackoffMaxSeconds;
        return Duration.ofSeconds(seconds);
    }
}
