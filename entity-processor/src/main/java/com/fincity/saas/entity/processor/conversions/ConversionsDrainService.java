package com.fincity.saas.entity.processor.conversions;

import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dto.ConversionEvent;
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

    public ConversionsDrainService(
            ConversionEventService eventService,
            ConversionActionMappingService mappingService,
            ConversionsDispatcherRegistry registry,
            TicketService ticketService,
            CampaignService campaignService,
            AbstractConnectionService connectionService) {
        this.eventService = eventService;
        this.mappingService = mappingService;
        this.registry = registry;
        this.ticketService = ticketService;
        this.campaignService = campaignService;
        this.connectionService = connectionService;
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
                            if (outcome == Outcome.DISPATCHED) dispatched.incrementAndGet();
                            else if (outcome == Outcome.FAILED) failed.incrementAndGet();
                            else skipped.incrementAndGet();
                        })
                        .onErrorResume(t -> {
                            logger.warn("Drain error for event {}: {}", event.getEventId(), t.getMessage());
                            failed.incrementAndGet();
                            return Mono.empty();
                        }))
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
                        .markFailed(event, "No dispatcher registered for platform " + event.getCampaignPlatform())
                        .thenReturn(Outcome.SKIPPED));
    }

    private Mono<Outcome> runDispatch(ConversionEvent event, AbstractConversionsDispatcher dispatcher) {

        return this.mappingService
                .read(event.getMappingId())
                .flatMap(mapping -> this.ticketService.read(event.getTicketId()).flatMap(ticket -> {
                    if (ticket.getCampaignId() == null) {
                        return this.eventService
                                .markFailed(event, "Ticket has no CAMPAIGN_ID; cannot resolve platform credentials")
                                .thenReturn(Outcome.SKIPPED);
                    }
                    return this.campaignService
                            .read(ticket.getCampaignId())
                            .flatMap(campaign -> this.connectionService
                                    .getConnectionOAuth2Token(
                                            campaign.getAppCode(),
                                            campaign.getClientCode(),
                                            connectionNameFor(event.getCampaignPlatform()))
                                    .flatMap(token -> dispatcher.dispatch(event, mapping, ticket, campaign, token))
                                    .flatMap(result -> result.success()
                                            ? this.eventService.markSent(event, result.message()).thenReturn(Outcome.DISPATCHED)
                                            : this.eventService.markFailed(event, result.message()).thenReturn(Outcome.FAILED)));
                }))
                .switchIfEmpty(this.eventService
                        .markFailed(event, "Mapping or ticket missing for this event")
                        .thenReturn(Outcome.SKIPPED));
    }

    private static String connectionNameFor(com.fincity.saas.entity.processor.enums.CampaignPlatform platform) {
        return switch (platform) {
            case GOOGLE -> "GOOGLE_API";
            case FACEBOOK -> "META_API";
            default -> throw new IllegalStateException("Unsupported platform for CAPI dispatch: " + platform);
        };
    }
}
