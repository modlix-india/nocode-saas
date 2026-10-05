package com.fincity.saas.entity.processor.conversions;

import java.util.Map;

/**
 * Outcome of one dispatcher attempt.
 *
 * <p>{@code success=true} → outbox row is marked SENT. A failure is marked FAILED with the error
 * message and a next-attempt backoff, unless it is {@code terminal}, in which case it is marked
 * SKIPPED and never attempted again. The raw platform response (if any) is stored verbatim on the
 * outbox row for later diagnostics.
 *
 * <h2>Why terminal exists</h2>
 *
 * <p>Before this flag, every failure was retryable, and {@code findDispatchable} selects
 * {@code STATUS IN (PENDING, FAILED)} with a backoff capped at one hour. A failure that can never
 * succeed therefore retried hourly for as long as the row existed.
 *
 * <p>Measured on production 2026-10-05: 60 rows stuck this way, 36,162 cumulative attempts, the
 * worst single row at 2,358 attempts, the oldest failing since 2026-06-29 — 98 days of hourly
 * retries. 49 of them were one cause, a campaign with no Meta pixel id, which is a configuration
 * fault no retry can resolve.
 *
 * <p>The cost was not the retry itself. Each attempt first calls {@code ensurePlatformContext} to
 * self-heal the missing id, which is a live call to the ad platform; it returned nothing every
 * time, so nothing was ever persisted and the next attempt asked again. Sixty of those arriving
 * together each hour, dispatched one at a time, pushed the drain batch past five minutes and left
 * unrelated endpoints timing out at the load balancer.
 *
 * <p>So the distinction this record now carries is the difference between a backlog and an outage.
 */
public record DispatchResult(
        boolean success, String message, Map<String, Object> platformResponse, boolean terminal) {

    public static DispatchResult ok(String message, Map<String, Object> response) {
        return new DispatchResult(true, message, response, false);
    }

    /** A failure worth retrying: a timeout, a 5xx, a rate limit, a token that can be refreshed. */
    public static DispatchResult fail(String message, Map<String, Object> response) {
        return new DispatchResult(false, message, response, false);
    }

    /**
     * A failure that will never succeed on retry, so the row is marked SKIPPED rather than left in
     * the retry pool.
     *
     * <p>Reserve this for faults in the DATA or CONFIGURATION rather than in the call: a missing
     * pixel id, an event the platform has rejected as malformed. Anything that a later attempt
     * could plausibly resolve — including an expired token, which the connection layer refreshes —
     * belongs in {@link #fail} instead. Marking something terminal that was merely transient
     * silently drops a conversion, which is the opposite failure and the harder one to notice.
     */
    public static DispatchResult failTerminal(String message, Map<String, Object> response) {
        return new DispatchResult(false, message, response, true);
    }
}
