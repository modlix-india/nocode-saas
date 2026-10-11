package com.fincity.saas.commons.core.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.fincity.saas.commons.mq.events.EventQueObject;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ScheduledEventTest extends AbstractSchedulerMongoTest {

    private static final String REMIND = "taskReminder";

    private ScheduledEvent row(String key) {
        return mongo().findOne(new Query(Criteria.where("key").is(key)), ScheduledEvent.class).block();
    }

    private ScheduledEventService.Scheduled schedule(Instance i, String key, Instant runAt, Map<String, Object> data) {
        return i.oneOffs().schedule(APP, "FIN", false, REMIND, data, runAt, key, "110").block();
    }

    private static Map<?, ?> marker(EventQueObject q) {
        return (Map<?, ?>) q.getData().get(EventScheduler.SCHEDULE_DATA_KEY);
    }

    @Test
    void aDueOneOffIsRaisedWithItsDataAndClosedOnSuccess() {

        Instance a = this.instance();
        this.schedule(a, "task-1", this.clock.instant().plusSeconds(60), Map.of("taskId", "t1"));

        a.scheduler().pass().block();
        assertEquals(0, this.events.raised.size(), "not due yet");

        this.clock.advanceSeconds(61);
        a.scheduler().pass().block();

        EventQueObject raised = this.events.of("FIN", REMIND).getFirst();
        assertEquals(APP, raised.getAppCode());
        assertEquals("t1", raised.getData().get("taskId"));
        assertEquals("task-1", marker(raised).get("key"));
        assertEquals(1, marker(raised).get("attempt"));
        assertEquals(ScheduledEvent.Status.CLAIMED, this.row("task-1").getStatus());

        a.scheduler().trackCompletion(raised, Mono.just(true)).block();
        assertEquals(ScheduledEvent.Status.DONE, this.row("task-1").getStatus());

        this.clock.advanceSeconds(3600);
        a.scheduler().pass().block();
        assertEquals(1, this.events.raised.size(), "a finished one-off is never raised again");
    }

    @Test
    void aFailureRetriesWithDoublingBackoffThenFails() {

        Instance a = this.instance();
        this.schedule(a, "outbox-9", this.clock.instant(), Map.of());

        Instant t0 = this.clock.instant();

        a.scheduler().pass().block();
        a.scheduler().trackCompletion(this.events.raised.getLast(), Mono.just(false)).block();
        ScheduledEvent after1 = this.row("outbox-9");
        assertEquals(ScheduledEvent.Status.PENDING, after1.getStatus());
        assertEquals(1, after1.getAttempts());
        assertEquals(t0.plusSeconds(30), after1.getRunAt());
        assertNotNull(after1.getLastError());

        // Not before the backoff.
        this.clock.set(t0.plusSeconds(29));
        a.scheduler().pass().block();
        assertEquals(1, this.events.raised.size());

        this.clock.set(t0.plusSeconds(30));
        a.scheduler().pass().block();
        assertEquals(2, this.events.raised.size());
        assertEquals(2, marker(this.events.raised.getLast()).get("attempt"));
        a.scheduler().trackCompletion(this.events.raised.getLast(), Mono.error(new IllegalStateException("boom")))
                .onErrorResume(e -> Mono.empty())
                .block();
        assertEquals(t0.plusSeconds(30 + 60), this.row("outbox-9").getRunAt(), "second wait doubles");

        this.clock.set(t0.plusSeconds(90));
        a.scheduler().pass().block();
        a.scheduler().trackCompletion(this.events.raised.getLast(), Mono.just(false)).block();

        ScheduledEvent failed = this.row("outbox-9");
        assertEquals(ScheduledEvent.Status.FAILED, failed.getStatus(), "3 attempts is the maximum here");
        assertEquals(3, failed.getAttempts());
        assertNotNull(failed.getFinishedAt());

        this.clock.advanceSeconds(7200);
        a.scheduler().pass().block();
        assertEquals(3, this.events.raised.size());
    }

    @Test
    void aClaimWhoseInstanceDiedIsReclaimedAfterItsLease() {

        Instance a = this.instance();
        this.schedule(a, "expiry-3", this.clock.instant(), Map.of());

        a.scheduler().pass().block();
        String firstToken = (String) marker(this.events.raised.getFirst()).get("claimToken");

        // Never reported back. Inside the lease nobody touches it.
        this.clock.advanceSeconds(200);
        this.instance().scheduler().pass().block();
        assertEquals(1, this.events.raised.size());

        this.clock.advanceSeconds(101);
        Instance b = this.instance();
        b.scheduler().pass().block();
        assertEquals(2, this.events.raised.size());
        EventQueObject second = this.events.raised.getLast();
        assertEquals(2, marker(second).get("attempt"));

        // The dead claim reporting late changes nothing; the live one closes the row.
        a.scheduler().trackCompletion(this.events.raised.getFirst(), Mono.just(true)).block();
        assertEquals(ScheduledEvent.Status.CLAIMED, this.row("expiry-3").getStatus());
        assertFalse(firstToken.equals(marker(second).get("claimToken")));
        b.scheduler().trackCompletion(second, Mono.just(true)).block();
        assertEquals(ScheduledEvent.Status.DONE, this.row("expiry-3").getStatus());
    }

    @Test
    void cancelStopsAPendingOneOff() {

        Instance a = this.instance();
        this.schedule(a, "reminder-5", this.clock.instant().plusSeconds(10), Map.of());

        assertTrue(a.oneOffs().cancel(APP, "FIN", false, "reminder-5").block());
        assertEquals(ScheduledEvent.Status.CANCELLED, this.row("reminder-5").getStatus());
        assertFalse(a.oneOffs().cancel(APP, "FIN", false, "reminder-5").block(), "already cancelled");
        assertFalse(a.oneOffs().cancel(APP, "FIN", false, "never-scheduled").block());
        assertFalse(a.oneOffs().cancel(APP, "FIN2", false, "reminder-5").block(), "another client's key");

        this.clock.advanceSeconds(60);
        a.scheduler().pass().block();
        assertEquals(0, this.events.raised.size());

        // A cancelled key can be scheduled again.
        assertNotNull(this.schedule(a, "reminder-5", this.clock.instant(), Map.of()).event());
        a.scheduler().pass().block();
        assertEquals(1, this.events.raised.size());
    }

    @Test
    void reschedulingAKeyReplacesThePendingOne() {

        Instance a = this.instance();
        Instant now = this.clock.instant();

        this.schedule(a, "nudge-1", now.plusSeconds(3600), Map.of("v", 1));
        this.schedule(a, "nudge-1", now.plusSeconds(7200), Map.of("v", 2));

        List<ScheduledEvent> rows = mongo().findAll(ScheduledEvent.class).collectList().block();
        assertEquals(1, rows.size());
        assertEquals(now.plusSeconds(7200), rows.getFirst().getRunAt());
        assertEquals(2, rows.getFirst().getData().get("v"));

        this.clock.set(now.plusSeconds(3700));
        a.scheduler().pass().block();
        assertEquals(0, this.events.raised.size(), "the replaced time no longer fires");
    }

    @Test
    void aKeyThatIsRunningIsNotReplacedUnderIt() {

        Instance a = this.instance();
        this.schedule(a, "sync-1", this.clock.instant(), Map.of());
        a.scheduler().pass().block();

        ScheduledEventService.Scheduled again = this.schedule(a, "sync-1", this.clock.instant(), Map.of("v", 2));
        assertNull(again.event(), "in flight, so refused");
        assertEquals(ScheduledEvent.Status.CLAIMED, this.row("sync-1").getStatus());

        // Once the claim's lease has run out it can be replaced.
        this.clock.advanceSeconds(301);
        assertNotNull(this.schedule(a, "sync-1", this.clock.instant(), Map.of("v", 3)).event());
        assertEquals(ScheduledEvent.Status.PENDING, this.row("sync-1").getStatus());
        assertEquals(0, this.row("sync-1").getAttempts());
    }

    @Test
    void aBlankKeyGetsOneThatCanCancelIt() {

        Instance a = this.instance();
        ScheduledEventService.Scheduled s = this.schedule(a, "", this.clock.instant().plusSeconds(60), Map.of());

        assertNotNull(s.key());
        assertFalse(s.key().isBlank());
        assertTrue(a.oneOffs().cancel(APP, "FIN", false, s.key()).block());
    }

    @Test
    void twoInstancesClaimEachOneOffExactlyOnce() {

        Instance a = this.instance();
        Instance b = this.instance();

        for (int i = 0; i < 60; i++)
            this.schedule(i % 2 == 0 ? a : b, "k" + i, this.clock.instant(), Map.of("i", i));

        Flux.range(0, 3)
                .flatMap(i -> Mono.zip(a.scheduler().pass(), b.scheduler().pass()))
                .blockLast();

        Map<Object, Long> perKey = this.events.raised.stream()
                .collect(Collectors.groupingBy(q -> marker(q).get("key"), Collectors.counting()));
        assertEquals(60, perKey.size());
        assertTrue(perKey.values().stream().allMatch(c -> c == 1), "each one-off raised once: " + perKey);
    }

    @Test
    void aDraftOneOffIsRaisedOntoTheDraftSurfaceUnderItsOwnKeys() {

        Instance a = this.instance();
        a.oneOffs().schedule(APP, "FIN", true, REMIND, Map.of(), this.clock.instant(), "same", "110").block();
        a.oneOffs().schedule(APP, "FIN", false, REMIND, Map.of(), this.clock.instant(), "same", "110").block();

        assertEquals(2, mongo().findAll(ScheduledEvent.class).collectList().block().size());

        a.scheduler().pass().block();
        assertEquals(2, this.events.raised.size());
        assertEquals(1, this.events.draftFlags.stream().filter(Boolean::booleanValue).count());
    }
}
