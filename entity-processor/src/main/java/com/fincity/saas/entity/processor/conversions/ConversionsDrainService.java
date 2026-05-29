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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    /** Drains one batch. Returns {@code {dispatched, failed, skipped}} counters. */
    public Mono<Map<String, Object>> drainBatch(int batchSize) {

        AtomicInteger dispatched = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();

        return this.eventService
                .findDispatchable(batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize)
                .concatMap(event -> this.dispatchOne(event)
                        .doOnNext(outcome -> {
                            switch (outcome) {
                                case DISPATCHED -> dispatched.incrementAndGet();
                                case FAILED -> failed.incrementAndGet();
                                case SKIPPED -> skipped.incrementAndGet();
                            }
                        })
                        .onErrorResume(t -> this.persistFailureAndContinue(event, failed, t)))
                .then(Mono.fromSupplier(() -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("dispatched", dispatched.get());
                    result.put("failed", failed.get());
                    result.put("skipped", skipped.get());
                    return result;
                }))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ConversionsDrainService.drainBatch"));
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
                .flatMap(result -> result.success()
                        ? this.eventService.markSent(event, result.message()).thenReturn(Outcome.DISPATCHED)
                        : this.eventService.markFailed(event, result.message()).thenReturn(Outcome.FAILED));
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
