package com.fincity.saas.commons.core.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.core.model.EventSchedule;
import com.fincity.saas.commons.mq.events.EventQueObject;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class EventSchedulerTest extends AbstractSchedulerMongoTest {

    private static final String SWEEP = "outboxSweep";

    private static EventSchedule everyMinute(boolean perClient) {
        return new EventSchedule().setCron("* * * * *").setPerClient(perClient);
    }

    private void perClientSweep(List<String> writers, List<String> withDefinition) {
        this.writeAccess.put(APP, writers);
        this.storeDefinition("SYSTEM", SWEEP, everyMinute(true));
        for (String c : withDefinition)
            this.definitions.put(c, effective(SWEEP, everyMinute(true)));
    }

    @Test
    void twoInstancesRacingRaiseEachTickExactlyOnce() {

        List<String> clients = IntStream.range(0, 12).mapToObj(i -> "CL" + i).toList();
        this.perClientSweep(clients, clients);

        Instance a = this.instance();
        Instance b = this.instance();
        AtomicInteger lost = new AtomicInteger();

        for (int minute = 0; minute < 5; minute++) {
            this.clock.set(Instant.parse("2026-10-09T09:00:05Z").plusSeconds(60L * minute));

            // Both instances, at the same moment, several times over.
            Flux.range(0, 3)
                    .flatMap(i -> Mono.zip(a.scheduler().pass(), b.scheduler().pass()))
                    .doOnNext(t -> lost.addAndGet(t.getT1().lost() + t.getT2().lost()))
                    .blockLast();

            // A tick that has been dealt with is not run again by a later pass either.
            a.scheduler().pass().block();

            // The listener finishing each run, so the next tick is not held back by its lease.
            Flux.fromIterable(this.events.raised)
                    .flatMap(q -> a.scheduler().trackCompletion(q, Mono.just(true)))
                    .blockLast();
        }

        assertTrue(lost.get() > 0, "the instances really did contend for the same ticks");

        for (String c : clients) {
            List<ScheduleRun> rows = this.runs(c, SWEEP);
            assertEquals(5, rows.size(), "one row per tick for " + c);
            assertEquals(5, this.events.of(c, SWEEP).size(), "one raised event per tick for " + c);
            assertEquals(5, rows.stream().map(ScheduleRun::getTickAt).distinct().count());
            assertTrue(rows.stream().allMatch(r -> r.getStatus() == ScheduleRun.Status.DONE));
        }
    }

    @Test
    void instancesWithSkewedClocksAgreeOnTheTick() {

        this.perClientSweep(List.of("FIN"), List.of("FIN"));

        Instance a = this.instance();
        Instance b = this.instance();

        this.clock.set("2026-10-09T09:00:02Z");
        a.scheduler().pass().block();
        this.clock.set("2026-10-09T09:00:41Z");
        b.scheduler().pass().block();

        List<ScheduleRun> rows = this.runs("FIN", SWEEP);
        assertEquals(1, rows.size());
        assertEquals(Instant.parse("2026-10-09T09:00:00Z"), rows.getFirst().getTickAt());
        assertEquals(1, this.events.of("FIN", SWEEP).size());
    }

    @Test
    void eachClientRunsOnItsOwnEffectiveSchedule() {

        String report = "dailyReport";
        EventSchedule base = new EventSchedule().setCron("0 9 * * *").setPerClient(true);

        this.writeAccess.put(APP, List.of("FIN", "FIN2", "FIN3", "FIN4"));
        this.storeDefinition("SYSTEM", report, base);
        this.storeDefinition("FIN2", report, new EventSchedule().setCron("0 10 * * *"));
        this.storeDefinition("FIN3", report, new EventSchedule().setEnabled(false));
        this.storeDefinition("FIN4", report, new EventSchedule().setTimezone("Asia/Kolkata"));

        this.definitions.put("FIN", effective(report, base));
        this.definitions.put("FIN2", effective(report, EventSchedule.merge(new EventSchedule().setCron("0 10 * * *"),
                base)));
        this.definitions.put("FIN3", effective(report, EventSchedule.merge(new EventSchedule().setEnabled(false),
                base)));
        this.definitions.put("FIN4", effective(report,
                EventSchedule.merge(new EventSchedule().setTimezone("Asia/Kolkata"), base)));

        Instance a = this.instance();

        // 09:00 Kolkata is 03:30 UTC: only FIN4.
        this.clock.set("2026-10-09T03:30:05Z");
        a.scheduler().pass().block();
        // 09:00 UTC: FIN, on the base cron.
        this.clock.set("2026-10-09T09:00:05Z");
        a.scheduler().pass().block();
        // 10:00 UTC: FIN2, on its own cron.
        this.clock.set("2026-10-09T10:00:05Z");
        a.scheduler().pass().block();

        assertEquals(List.of(Instant.parse("2026-10-09T03:30:00Z")), ticks("FIN4", report));
        assertEquals(List.of(Instant.parse("2026-10-09T09:00:00Z")), ticks("FIN", report));
        assertEquals(List.of(Instant.parse("2026-10-09T10:00:00Z")), ticks("FIN2", report));
        assertEquals(List.of(), ticks("FIN3", report), "enabled: false on the override stops that client");
        assertEquals(0, this.events.of("FIN3", report).size());
    }

    @Test
    void aClientWithoutWriteAccessGetsNoRun() {

        // OUTSIDER resolves an effective definition but is not on the write-access list.
        this.perClientSweep(List.of("FIN", "FIN2"), List.of("FIN", "FIN2", "OUTSIDER"));

        this.instance().scheduler().pass().block();

        assertEquals(1, this.events.of("FIN", SWEEP).size());
        assertEquals(1, this.events.of("FIN2", SWEEP).size());
        assertEquals(0, this.events.of("OUTSIDER", SWEEP).size());
        assertEquals(List.of(), this.runs("OUTSIDER", SWEEP));
    }

    @Test
    void aSingleRunScheduleRunsOnlyForTheClientHoldingIt() {

        String cleanup = "nightlyCleanup";
        EventSchedule schedule = new EventSchedule().setCron("* * * * *");
        this.storeDefinition("FIN", cleanup, schedule);
        this.definitions.put("FIN", effective(cleanup, schedule));
        this.definitions.put("FIN2", effective(cleanup, schedule));

        this.instance().scheduler().pass().block();

        assertEquals(1, this.events.of("FIN", cleanup).size());
        assertEquals(0, this.events.of("FIN2", cleanup).size());
        assertEquals(0, this.writeAccessCalls.get(), "no perClient anywhere, so security is never asked");
    }

    @Test
    void theRaisedEventCarriesItsRunAndCompletionClosesIt() {

        this.perClientSweep(List.of("FIN"), List.of("FIN"));
        Instance a = this.instance();
        a.scheduler().pass().block();

        EventQueObject raised = this.events.of("FIN", SWEEP).getFirst();
        assertEquals(APP, raised.getAppCode());
        assertNull(raised.getAuthentication());
        Map<?, ?> marker = (Map<?, ?>) raised.getData().get(EventScheduler.SCHEDULE_DATA_KEY);
        assertEquals(EventScheduler.KIND_CRON, marker.get("kind"));
        assertEquals("2026-10-09T09:00:00Z", marker.get("tickAt"));

        assertEquals(ScheduleRun.Status.RUNNING, this.runs("FIN", SWEEP).getFirst().getStatus());
        a.scheduler().trackCompletion(raised, Mono.just(true)).block();
        assertEquals(ScheduleRun.Status.DONE, this.runs("FIN", SWEEP).getFirst().getStatus());

        this.clock.advanceSeconds(60);
        a.scheduler().pass().block();
        EventQueObject second = this.events.of("FIN", SWEEP).get(1);
        a.scheduler().trackCompletion(second, Mono.just(false)).block();
        assertEquals(ScheduleRun.Status.FAILED, this.runs("FIN", SWEEP).get(1).getStatus());

        // An event that did not come from the scheduler passes straight through.
        EventQueObject plain = new EventQueObject().setEventName("other").setData(Map.of());
        assertEquals(Boolean.TRUE, a.scheduler().trackCompletion(plain, Mono.just(true)).block());
    }

    @Test
    void aTickWhosePredecessorIsStillRunningIsSkippedUntilTheLeaseRunsOut() {

        this.perClientSweep(List.of("FIN"), List.of("FIN"));
        Instance a = this.instance(this.settings().setLeaseSeconds(150));

        // 09:00 claimed and never reported back: the instance died, or the sweep is slow.
        a.scheduler().pass().block();

        // 09:01 and 09:02 fall inside the lease and are recorded as SKIPPED.
        this.clock.set("2026-10-09T09:01:05Z");
        a.scheduler().pass().block();
        this.clock.set("2026-10-09T09:02:05Z");
        a.scheduler().pass().block();

        // 09:03 is past 09:00:10 + 150s, so the dead claim no longer blocks.
        this.clock.set("2026-10-09T09:03:05Z");
        a.scheduler().pass().block();

        List<ScheduleRun> rows = this.runs("FIN", SWEEP);
        assertEquals(List.of(ScheduleRun.Status.RUNNING, ScheduleRun.Status.SKIPPED, ScheduleRun.Status.SKIPPED,
                ScheduleRun.Status.RUNNING), rows.stream().map(ScheduleRun::getStatus).toList());
        assertEquals(2, this.events.of("FIN", SWEEP).size());
    }

    @Test
    void anOutageCatchesUpOnlyTheLatestMissedTick() {

        String sync = "campaignSync";
        EventSchedule everyTen = new EventSchedule().setCron("*/10 * * * *").setPerClient(true);
        this.writeAccess.put(APP, List.of("FIN"));
        this.storeDefinition("SYSTEM", sync, everyTen);
        this.definitions.put("FIN", effective(sync, everyTen));

        Instance a = this.instance();
        a.scheduler().pass().block();
        a.scheduler().trackCompletion(this.events.of("FIN", sync).getFirst(), Mono.just(true)).block();

        // Down from 09:00 to 09:35, missing 09:10, 09:20 and 09:30. A fresh instance,
        // so nothing is remembered in memory either.
        this.clock.set("2026-10-09T09:35:00Z");
        this.instance().scheduler().pass().block();

        assertEquals(List.of(Instant.parse("2026-10-09T09:00:00Z"), Instant.parse("2026-10-09T09:30:00Z")),
                ticks("FIN", sync));
        assertEquals(2, this.events.of("FIN", sync).size());
    }

    @Test
    void aNewScheduleDoesNotFireForATickItNeverHad() {

        String report = "dailyReport";
        EventSchedule daily = new EventSchedule().setCron("0 2 * * *").setPerClient(true);
        this.writeAccess.put(APP, List.of("FIN"));
        this.storeDefinition("SYSTEM", report, daily);
        this.definitions.put("FIN", effective(report, daily));

        // Deployed at 09:00; 02:00 today is inside the catch-up window, but no run was ever recorded.
        Instance a = this.instance();
        a.scheduler().pass().block();
        assertEquals(List.of(), ticks("FIN", report));

        this.clock.set("2026-10-10T02:00:20Z");
        a.scheduler().pass().block();
        assertEquals(List.of(Instant.parse("2026-10-10T02:00:00Z")), ticks("FIN", report));
    }

    @Test
    void aFailedRaiseIsRecordedAsFailed() {

        this.perClientSweep(List.of("FIN"), List.of("FIN"));
        this.events.failing = true;

        EventScheduler.PassReport report = this.instance().scheduler().pass().block();

        assertEquals(1, report.failed());
        ScheduleRun row = this.runs("FIN", SWEEP).getFirst();
        assertEquals(ScheduleRun.Status.FAILED, row.getStatus());
        assertTrue(row.getError().contains("broker down"));
    }

    private List<Instant> ticks(String clientCode, String eventName) {
        return this.runs(clientCode, eventName).stream()
                .filter(r -> r.getStatus() != ScheduleRun.Status.SKIPPED)
                .map(ScheduleRun::getTickAt)
                .collect(Collectors.toList());
    }
}
