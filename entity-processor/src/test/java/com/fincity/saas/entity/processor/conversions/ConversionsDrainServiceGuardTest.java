package com.fincity.saas.entity.processor.conversions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fincity.saas.entity.processor.dao.CampaignDAO;
import com.fincity.saas.entity.processor.platform.AdPlatformRegistry;
import com.fincity.saas.entity.processor.service.CampaignService;
import com.fincity.saas.entity.processor.service.ConversionActionMappingService;
import com.fincity.saas.entity.processor.service.ConversionEventService;
import com.fincity.saas.entity.processor.service.TicketService;
import com.fincity.saas.entity.processor.service.commons.AbstractConnectionService;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * The overlap guard is the part of {@link ConversionsDrainService} that stops a slow batch becoming
 * an outage, so it gets a test that does not depend on anything being slow.
 *
 * <p>On 2026-10-05 a batch that normally took 8-9s ran past 300s because a partner API degraded.
 * The worker invokes this every five minutes regardless, so runs stacked and the accumulation —
 * not the slowness itself — is what pushed unrelated endpoints into 60s nginx timeouts. Reproducing
 * that with real delays would be slow and flaky; a source that simply never completes holds the
 * guard open deterministically and asks the only question that matters: does a second run start
 * while the first is still going?
 */
class ConversionsDrainServiceGuardTest {

    private static final int BATCH = 10;

    private ConversionsDrainService serviceWith(Flux<?> dispatchable) {

        ConversionEventService eventService = mock(ConversionEventService.class);
        when(eventService.findDispatchable(BATCH)).thenAnswer(inv -> dispatchable);

        ConversionsDrainService service = new ConversionsDrainService(
                eventService,
                mock(ConversionActionMappingService.class),
                mock(ConversionsDispatcherRegistry.class),
                mock(TicketService.class),
                mock(CampaignService.class),
                mock(AbstractConnectionService.class),
                mock(AdPlatformRegistry.class),
                mock(CampaignDAO.class));

        // @Value fields are not populated outside Spring, and zero would make the budget expire
        // before the first event and the per-event timeout fire instantly.
        ReflectionTestUtils.setField(service, "perEventTimeoutSeconds", 15L);
        ReflectionTestUtils.setField(service, "batchBudgetSeconds", 120L);
        return service;
    }

    @Test
    void secondDrainIsRefusedWhileTheFirstIsStillRunning() {

        ConversionsDrainService service = this.serviceWith(Flux.never());

        // Holds the guard: never() emits nothing and never completes, which is the in-memory
        // equivalent of a batch stuck on a partner API.
        Disposable inFlight = service.drainBatch(BATCH).subscribe();

        Map<String, Object> second = service.drainBatch(BATCH).block();

        assertNotNull(second, "a refused drain must still answer rather than hang");
        assertEquals(Boolean.TRUE, second.get("busy"), "the second drain should report itself busy");
        assertEquals(0, second.get("dispatched"));
        assertEquals(0, second.get("failed"));
        assertEquals(0, second.get("skipped"));

        inFlight.dispose();
    }

    @Test
    void theGuardIsReleasedWhenTheRunEnds() {

        AtomicReference<Flux<?>> source = new AtomicReference<>(Flux.never());
        ConversionsDrainService service = this.serviceWith(Flux.defer(() -> source.get()));

        Disposable inFlight = service.drainBatch(BATCH).subscribe();
        assertEquals(
                Boolean.TRUE,
                service.drainBatch(BATCH).block().get("busy"),
                "precondition: the guard is held while the first run is open");

        // Cancellation, not completion: a dropped client or a shutdown must release the guard too,
        // otherwise one abandoned run disables draining until the instance restarts. doFinally
        // covers cancel, error and completion alike, and this is the case most easily missed.
        inFlight.dispose();

        source.set(Flux.empty());
        Map<String, Object> after = service.drainBatch(BATCH).block();

        assertNotNull(after);
        assertNull(after.get("busy"), "a drain after release must not report busy");
        assertEquals(0, after.get("dispatched"));
        assertEquals(Boolean.FALSE, after.get("truncated"), "an empty batch is not a truncated one");
    }

    @Test
    void anEmptyOutboxReportsZeroesRatherThanFailing() {

        Map<String, Object> result = this.serviceWith(Flux.empty()).drainBatch(BATCH).block();

        assertNotNull(result);
        assertEquals(0, result.get("dispatched"));
        assertEquals(0, result.get("failed"));
        assertEquals(0, result.get("skipped"));
        assertTrue(result.containsKey("truncated"));
    }
}
