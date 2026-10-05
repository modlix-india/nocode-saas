package com.fincity.saas.entity.processor.conversions;

import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.CampaignDAO;
import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.dto.ConversionEvent;
import com.fincity.saas.entity.processor.platform.AbstractAdPlatformService;
import com.fincity.saas.entity.processor.platform.AdPlatformRegistry;
import com.fincity.saas.entity.processor.service.CampaignService;
import com.fincity.saas.entity.processor.service.ConversionActionMappingService;
import com.fincity.saas.entity.processor.service.ConversionEventService;
import com.fincity.saas.entity.processor.service.TicketService;
import com.fincity.saas.entity.processor.service.commons.AbstractConnectionService;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Reads pending conversion events from the outbox, looks up the related ticket
 * + mapping + campaign, fetches the platform OAuth token, dispatches via the
 * platform-specific {@link AbstractConversionsDispatcher}, and marks the outbox
 * row {@code SENT}/{@code FAILED} based on the result.
 *
 * <p>Called by the worker on every {@code CONVERSIONS_API_DISPATCH} task tick.
 */
@Service
public class ConversionsDrainService {

    private static final Logger logger = LoggerFactory.getLogger(ConversionsDrainService.class);
    private static final int DEFAULT_BATCH_SIZE = 50;

    /**
     * Guards against a run being started while the previous one is still going. See the note in
     * {@link #drainBatch(int)} for why overlapping runs are the dangerous part.
     */
    private final AtomicBoolean draining = new AtomicBoolean();

    /**
     * Ceiling on a single event's dispatch.
     *
     * <p>Measured, not chosen: a healthy batch of 50 completes in 8-9s, so an event averages about
     * 170ms. Fifteen seconds is roughly ninety times that — long enough that it can only be reached
     * by a dependency that is genuinely stuck, short enough that fifty of them cannot outlast the
     * worker's five-minute interval.
     */
    @Value("${entity.processor.conversions.drain.perEventTimeoutSeconds:15}")
    private long perEventTimeoutSeconds;

    /**
     * Wall-clock ceiling on the whole batch.
     *
     * <p>Two minutes against a five-minute dispatch interval. The gap is deliberate: the batch must
     * finish well inside its own cadence even when every event is slow, because a job that cannot
     * finish before it is next invoked is the failure this class caused. Worst case is this budget
     * plus one in-flight event timeout, so about 135s.
     */
    @Value("${entity.processor.conversions.drain.batchBudgetSeconds:120}")
    private long batchBudgetSeconds;

    private final ConversionEventService eventService;
    private final ConversionActionMappingService mappingService;
    private final ConversionsDispatcherRegistry registry;
    private final TicketService ticketService;
    private final CampaignService campaignService;
    private final AbstractConnectionService connectionService;
    private final AdPlatformRegistry adPlatformRegistry;
    private final CampaignDAO campaignDAO;

    public ConversionsDrainService(
            ConversionEventService eventService,
            ConversionActionMappingService mappingService,
            ConversionsDispatcherRegistry registry,
            TicketService ticketService,
            CampaignService campaignService,
            AbstractConnectionService connectionService,
            AdPlatformRegistry adPlatformRegistry,
            CampaignDAO campaignDAO) {
        this.eventService = eventService;
        this.mappingService = mappingService;
        this.registry = registry;
        this.ticketService = ticketService;
        this.campaignService = campaignService;
        this.connectionService = connectionService;
        this.adPlatformRegistry = adPlatformRegistry;
        this.campaignDAO = campaignDAO;
    }

    /**
     * Drains one batch. Returns {@code {dispatched, failed, skipped}} counters.
     *
     * <h2>Why this is bounded three ways</h2>
     *
     * <p>On 2026-10-05 this method took production down twice. It had no per-event timeout, no
     * wall-clock budget and no guard against overlapping runs, and the three together turn one slow
     * partner API into a service-wide outage.
     *
     * <p>The sequence, from the metrics: Meta's API slowed around 12:47 (a burst of adspixels and
     * CAPI warnings in three seconds). Because {@link #dispatchOne} is chained with
     * {@code concatMap} — strictly one event at a time — and nothing bounded a single dispatch,
     * a batch that normally finished in 8-9s ran past 300s. The worker calls this endpoint every
     * five minutes, so runs stopped finishing inside their own interval and began to stack. By
     * 13:10 ordinary reads on fifteen unrelated endpoints were timing out at nginx's 60s, while
     * the JVM sat at idle CPU with an empty connection pool: the work was parked on an external
     * call, holding no thread, visible to nothing.
     *
     * <p>{@code concatMap} is kept deliberately. Dispatch order matters for the outbox and
     * parallelising it would multiply load on the very API that is already struggling. The fix is
     * to bound it, not to widen it.
     */
    public Mono<Map<String, Object>> drainBatch(int batchSize) {

        return Mono.defer(() -> {
            // One drain per instance at a time.
            //
            // Without this, a run that overruns the worker's five-minute tick simply gets another
            // one laid on top of it, and they accumulate for as long as the slowness lasts. That
            // accumulation is what turns a slow background job into a front-of-house outage.
            //
            // Per INSTANCE, not cluster-wide: two app instances can still drain concurrently, and
            // that is unchanged behaviour which findDispatchable's row claiming already handles.
            // This only stops a single instance piling runs on itself.
            if (!this.draining.compareAndSet(false, true)) {
                logger.warn(
                        "Conversions drain skipped: a previous batch is still running. This means the"
                                + " last batch overran the worker's dispatch interval.");
                Map<String, Object> busy = new HashMap<>();
                busy.put("dispatched", 0);
                busy.put("failed", 0);
                busy.put("skipped", 0);
                busy.put("busy", true);
                return Mono.just(busy);
            }

            return this.drainBatchGuarded(batchSize).doFinally(signal -> this.draining.set(false));
        });
    }

    private Mono<Map<String, Object>> drainBatchGuarded(int batchSize) {

        AtomicInteger dispatched = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();

        long deadline = System.nanoTime() + this.batchBudget().toNanos();

        return this.eventService
                .findDispatchable(batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize)
                // Stop pulling new events once the budget is spent. concatMap means the source is
                // pulled one at a time, so this is evaluated between dispatches and the batch ends
                // at a clean boundary rather than being cancelled mid-flight. Whatever is left
                // stays in the outbox and is picked up by the next tick, which is what an outbox
                // is for.
                .takeWhile(event -> System.nanoTime() < deadline)
                .concatMap(event -> this.dispatchOne(event)
                        // One slow event must not be able to consume the whole batch.
                        //
                        // Safe to retry on timeout: MetaConversionsDispatcher sends the outbox
                        // row's own event_id, and Meta deduplicates on it, so an event that was in
                        // fact accepted just before this fired is discarded on the resend rather
                        // than double counted. That property is what makes failing fast here the
                        // right call instead of waiting to be certain.
                        .timeout(this.perEventTimeout())
                        .doOnNext(outcome -> {
                            switch (outcome) {
                                case DISPATCHED -> dispatched.incrementAndGet();
                                case FAILED -> failed.incrementAndGet();
                                case SKIPPED -> skipped.incrementAndGet();
                            }
                        })
                        .onErrorResume(t -> this.persistFailureAndContinue(event, failed, t)))
                .then(Mono.fromSupplier(() -> {
                    boolean truncated = System.nanoTime() >= deadline;
                    if (truncated)
                        logger.warn(
                                "Conversions drain hit its {}s budget after {} dispatched, {} failed,"
                                        + " {} skipped; the remainder stays in the outbox.",
                                this.batchBudget().toSeconds(),
                                dispatched.get(),
                                failed.get(),
                                skipped.get());

                    Map<String, Object> result = new HashMap<>();
                    result.put("dispatched", dispatched.get());
                    result.put("failed", failed.get());
                    result.put("skipped", skipped.get());
                    result.put("truncated", truncated);
                    return result;
                }))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ConversionsDrainService.drainBatch"));
    }

    private Duration perEventTimeout() {
        return Duration.ofSeconds(this.perEventTimeoutSeconds);
    }

    private Duration batchBudget() {
        return Duration.ofSeconds(this.batchBudgetSeconds);
    }

    private enum Outcome {
        DISPATCHED,
        FAILED,
        SKIPPED
    }

    private Mono<Outcome> dispatchOne(ConversionEvent event) {

        return this.registry
                .get(event.getCampaignPlatform())
                .map(dispatcher -> this.runDispatch(event, dispatcher))
                .orElseGet(() -> this.eventService
                        // Terminal: a missing dispatcher will never appear via retry.
                        .markSkipped(event, "No dispatcher registered for platform " + event.getCampaignPlatform())
                        .thenReturn(Outcome.SKIPPED));
    }

    private Mono<Outcome> runDispatch(ConversionEvent event, AbstractConversionsDispatcher dispatcher) {

        // Worker-only no-auth lookups; the user-facing read variant would reject the
        // no-JWT context with a login-required error. Same pattern MetricsSyncService uses.
        return this.mappingService
                .findById(event.getMappingId())
                .flatMap(mapping -> this.ticketService
                        .findById(event.getTicketId())
                        .flatMap(ticket -> dispatchForTicket(event, dispatcher, mapping, ticket)))
                // Terminal: the referenced mapping or ticket has been deleted; no retry will revive it.
                .switchIfEmpty(this.eventService
                        .markSkipped(event, "Mapping or ticket missing for this event")
                        .thenReturn(Outcome.SKIPPED));
    }

    private Mono<Outcome> dispatchForTicket(
            ConversionEvent event,
            AbstractConversionsDispatcher dispatcher,
            com.fincity.saas.entity.processor.dto.ConversionActionMapping mapping,
            com.fincity.saas.entity.processor.dto.Ticket ticket) {

        if (ticket.getCampaignId() == null) {
            // Terminal: a ticket without campaign attribution will never gain one on retry.
            // The enqueue gate now prevents this, but legacy rows exist.
            return this.eventService
                    .markSkipped(event, "Ticket has no CAMPAIGN_ID; cannot resolve platform credentials")
                    .thenReturn(Outcome.SKIPPED);
        }
        return this.campaignService
                .findById(ticket.getCampaignId())
                .flatMap(campaign -> this.connectionService
                        .getMarketingPlatformOAuth2Token(
                                campaign.getClientCode(), connectionNameFor(event.getCampaignPlatform()))
                        .flatMap(token -> resolveAndDispatch(event, dispatcher, mapping, ticket, campaign, token)));
    }

    private Mono<Outcome> resolveAndDispatch(
            ConversionEvent event,
            AbstractConversionsDispatcher dispatcher,
            com.fincity.saas.entity.processor.dto.ConversionActionMapping mapping,
            com.fincity.saas.entity.processor.dto.Ticket ticket,
            Campaign campaign,
            String token) {

        // Self-heal platform context (e.g. Meta pixel id) before dispatch — same pattern
        // MetricsSyncService uses. Persist any newly-resolved ids so the next dispatch
        // doesn't re-query the platform.
        final String origAccountId = campaign.getPlatformAccountId();
        final String origLoginId = campaign.getPlatformLoginId();
        final String origDatasetId = campaign.getPlatformDatasetId();
        AbstractAdPlatformService platform = this.adPlatformRegistry.getService(event.getCampaignPlatform());

        return platform
                .ensurePlatformContext(campaign, token)
                .flatMap(resolved -> persistResolvedIfChanged(resolved, origAccountId, origLoginId, origDatasetId)
                        .thenReturn(resolved))
                .flatMap(resolved -> dispatcher.dispatch(event, mapping, ticket, resolved, token))
                .flatMap(result -> this.recordOutcome(event, result));
    }

    /**
     * Routes a dispatcher result to the outbox, keeping terminal failures out of the retry pool.
     *
     * <p>A retryable failure stays FAILED with a backoff and is picked up again. A terminal one is
     * marked SKIPPED, which {@code findDispatchable} does not select, so it is never attempted
     * again. That difference is what stops a configuration fault becoming an hourly load: before
     * it existed, 60 such rows had accumulated 36,162 attempts between them.
     *
     * <p>The row is kept rather than deleted, with its message, so the events that never reached
     * the platform can still be found and requeued once the underlying configuration is corrected.
     */
    private Mono<Outcome> recordOutcome(ConversionEvent event, DispatchResult result) {

        if (result.success())
            return this.eventService.markSent(event, result.message()).thenReturn(Outcome.DISPATCHED);

        if (result.terminal()) {
            logger.warn(
                    "Conversion event {} will not be retried: {}. The row is kept as SKIPPED and can be"
                            + " requeued once the cause is corrected.",
                    event.getId(),
                    result.message());
            return this.eventService.markSkipped(event, result.message()).thenReturn(Outcome.SKIPPED);
        }

        return this.eventService.markFailed(event, result.message()).thenReturn(Outcome.FAILED);
    }

    /** Mirror of {@code MetricsSyncService.persistResolvedIfChanged} — keeps the two self-heal paths in sync. */
    private Mono<Void> persistResolvedIfChanged(
            Campaign resolved, String origAccountId, String origLoginId, String origDatasetId) {
        String accountId = diff(origAccountId, resolved.getPlatformAccountId());
        String loginId = diff(origLoginId, resolved.getPlatformLoginId());
        String datasetId = diff(origDatasetId, resolved.getPlatformDatasetId());
        if (accountId == null && loginId == null && datasetId == null) {
            return Mono.empty();
        }
        return this.campaignDAO
                .updatePlatformIds(resolved.getId(), accountId, loginId, datasetId)
                .doOnNext(n -> logger.info(
                        "Drain backfilled platform-context for campaign id={} (rows={}, accountId={}, loginId={}, datasetId={})",
                        resolved.getId(), n, accountId, loginId, datasetId))
                .then()
                .onErrorResume(e -> {
                    logger.warn("Failed to persist resolved platform-context for campaign id={}: {}",
                            resolved.getId(), e.toString());
                    return Mono.empty();
                });
    }

    /** Returns {@code resolved} only when it's a non-blank value that differs from {@code original}. */
    private static String diff(String original, String resolved) {
        if (resolved == null || resolved.isBlank()) return null;
        if (resolved.equals(original)) return null;
        return resolved;
    }

    private Mono<Outcome> persistFailureAndContinue(ConversionEvent event, AtomicInteger failed, Throwable t) {
        logger.warn("Drain error for event {}: {}", event.getEventId(), t.getMessage(), t);
        failed.incrementAndGet();
        return this.eventService
                .markFailed(event, truncate(t.getMessage()))
                .thenReturn(Outcome.FAILED)
                .onErrorResume(persistEx -> {
                    logger.warn("Failed to persist error on event {}: {}",
                            event.getEventId(), persistEx.getMessage());
                    return Mono.just(Outcome.FAILED);
                });
    }

    /** Keep STATUS_MESSAGE within the TEXT column's safe bound. */
    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 4000 ? s.substring(0, 4000) : s;
    }

    private static String connectionNameFor(com.fincity.saas.entity.processor.enums.CampaignPlatform platform) {
        return switch (platform) {
            case GOOGLE -> "GOOGLE_API";
            case FACEBOOK -> "META_API";
            default -> throw new IllegalStateException("Unsupported platform for CAPI dispatch: " + platform);
        };
    }
}
