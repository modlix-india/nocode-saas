package com.fincity.saas.commons.core.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import com.fincity.saas.commons.core.model.EventSchedule;
import com.fincity.saas.commons.mq.events.EventQueObject;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The scheduler running what it claims in its own JVM: an instance claims a
 * (client, tick) only while it has a free run slot, so the work spreads over the
 * instances by what each can take, with nothing pinned and each tick still run
 * exactly once.
 */
class EventSchedulerInProcessTest extends AbstractSchedulerMongoTest {

    private static final String SWEEP = "outboxSweep";

    /** What one instance ran, standing in for EventsQueListener.run. */
    private static final class Runner implements ScheduledEventRunner {

        private final String name;
        private final Duration work;
        private final List<String[]> log;
        private EventScheduler scheduler;

        Runner(String name, Duration work, List<String[]> log) {
            this.name = name;
            this.work = work;
            this.log = log;
        }

        @Override
        public Mono<Boolean> run(EventQueObject qob) {
            // The real listener closes the run through trackCompletion; so does this.
            return this.scheduler.trackCompletion(qob, Mono.delay(this.work)
                    .doOnNext(t -> this.log.add(new String[] { this.name, qob.getClientCode(),
                            qob.getEventName() }))
                    .thenReturn(Boolean.TRUE));
        }
    }

    private final List<String[]> ran = new CopyOnWriteArrayList<>();

    private Instance inProcess(String name, int slots, Duration work) {
        Instance i = this.instance(this.settings().setRunConcurrency(slots));
        Runner r = new Runner(name, work, this.ran);
        r.scheduler = i.scheduler();
        i.scheduler().setRunner(r);
        return i;
    }

    private void perClientSweep(List<String> clients) {
        this.writeAccess.put(APP, clients);
        this.storeDefinition("SYSTEM", SWEEP, new EventSchedule().setCron("* * * * *").setPerClient(true));
        for (String c : clients)
            this.definitions.put(c, effective(SWEEP, new EventSchedule().setCron("* * * * *").setPerClient(true)));
    }

    private static void awaitIdle(Instance... instances) {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            boolean idle = true;
            for (Instance i : instances)
                idle &= i.scheduler().inFlight() == 0;
            if (idle)
                return;
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new AssertionError("runs did not finish");
    }

    private Map<String, Integer> perInstance() {
        Map<String, Integer> m = new ConcurrentHashMap<>();
        for (String[] r : this.ran)
            m.merge(r[0], 1, Integer::sum);
        return m;
    }

    @Test
    void threeInstancesRunEachClientTickOnceAndShareTheWork() {

        List<String> clients = IntStream.range(0, 30).mapToObj(i -> "CL" + i).toList();
        this.perClientSweep(clients);

        Instance a = this.inProcess("a", 4, Duration.ofMillis(40));
        Instance b = this.inProcess("b", 4, Duration.ofMillis(40));
        Instance c = this.inProcess("c", 4, Duration.ofMillis(40));

        this.clock.set("2026-10-09T09:00:05Z");

        // Passes keep coming while the runs are in flight, as the 30-second timer
        // would on three JVMs; each instance only claims what its slots allow.
        for (int round = 0; round < 40 && this.ran.size() < clients.size(); round++) {
            Flux.merge(a.scheduler().pass(), b.scheduler().pass(), c.scheduler().pass()).blockLast();
            try {
                Thread.sleep(15);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        awaitIdle(a, b, c);

        for (String cl : clients) {
            List<ScheduleRun> rows = this.runs(cl, SWEEP);
            assertEquals(1, rows.size(), "one claim for " + cl);
            assertEquals(ScheduleRun.Status.DONE, rows.getFirst().getStatus(), "closed for " + cl);
            assertEquals(1, this.ran.stream().filter(r -> r[1].equals(cl)).count(), "ran once for " + cl);
        }

        Map<String, Integer> split = this.perInstance();
        assertEquals(3, split.size(), "every instance took a share: " + split);
        split.values().forEach(n -> assertTrue(n >= 4, "no instance starved: " + split));
        assertEquals(0, this.events.raised.size(), "nothing went to the queue");
    }

    @Test
    void aSlowInstanceTakesLess() {

        List<String> clients = IntStream.range(0, 24).mapToObj(i -> "CL" + i).toList();
        this.perClientSweep(clients);

        Instance slow = this.inProcess("slow", 2, Duration.ofMillis(400));
        Instance fast = this.inProcess("fast", 2, Duration.ofMillis(10));

        this.clock.set("2026-10-09T09:00:05Z");

        for (int round = 0; round < 200 && this.ran.size() + slow.scheduler().inFlight() < clients.size(); round++) {
            Flux.merge(slow.scheduler().pass(), fast.scheduler().pass()).blockLast();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        awaitIdle(slow, fast);

        Map<String, Integer> split = this.perInstance();
        assertEquals(clients.size(), this.ran.size(), "every client ran once");
        assertTrue(split.getOrDefault("fast", 0) > split.getOrDefault("slow", 0),
                "the fast instance took more: " + split);
    }

    @Test
    void aBusyInstanceLeavesTheRestForLater() {

        this.perClientSweep(List.of("A1", "A2", "A3"));
        Instance only = this.inProcess("only", 1, Duration.ofMillis(200));

        this.clock.set("2026-10-09T09:00:05Z");

        EventScheduler.PassReport first = only.scheduler().pass().block();
        assertEquals(1, first.raised(), "one slot, one claim");
        assertEquals(2, first.busy(), "the other two left unclaimed");
        assertEquals(1, this.runs("A1", SWEEP).size() + this.runs("A2", SWEEP).size()
                + this.runs("A3", SWEEP).size(), "only the claimed one has a row");

        // Later passes, still inside the fresh window, take the rest one at a time.
        for (int i = 0; i < 20 && this.ran.size() < 3; i++) {
            awaitIdle(only);
            only.scheduler().pass().block();
        }
        awaitIdle(only);

        for (String cl : List.of("A1", "A2", "A3"))
            assertEquals(1, this.runs(cl, SWEEP).size(), "ran once: " + cl);
        assertEquals(3, this.ran.size());
    }

    @Test
    void oneOffsAreClaimedOnePerFreeSlot() {

        Instance a = this.inProcess("a", 2, Duration.ofMillis(60));
        Instance b = this.inProcess("b", 2, Duration.ofMillis(60));

        for (int k = 0; k < 10; k++)
            a.oneOffs().schedule(APP, "FIN", false, "remind", Map.of("n", k), this.clock.instant(), "k" + k, "110")
                    .block();

        EventScheduler.PassReport firstA = a.scheduler().pass().block();
        assertEquals(2, firstA.oneOffsRaised(), "two slots, two claims");

        for (int round = 0; round < 50 && this.ran.size() < 10; round++) {
            Flux.merge(a.scheduler().pass(), b.scheduler().pass()).blockLast();
            try {
                Thread.sleep(15);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        awaitIdle(a, b);

        assertEquals(10, this.ran.size(), "each one-off ran once");
        assertEquals(10, this.ran.stream().map(r -> r[1] + r[2]).count());
        assertEquals(2, this.perInstance().size(), "both instances took some: " + this.perInstance());
        long done = mongo().findAll(ScheduledEvent.class).filter(e -> e.getStatus() == ScheduledEvent.Status.DONE)
                .count().block();
        assertEquals(10, done);
    }

    @Test
    void spreadShiftsEachClientByAFixedOffsetInsideThePeriod() {

        CronExpression every5 = CronGrid.parse("*/5 * * * *");
        AtomicInteger distinct = new AtomicInteger();
        java.util.Set<Long> offsets = new java.util.HashSet<>();

        for (int i = 0; i < 50; i++) {
            long off = CronGrid.spreadOffsetSeconds(every5, ZoneOffset.UTC, APP + "\u0000CL" + i + "\u0000" + SWEEP);
            assertTrue(off >= 0 && off < 300, "offset inside the 5-minute period: " + off);
            if (offsets.add(off))
                distinct.incrementAndGet();
        }
        assertTrue(distinct.get() > 30, "clients spread over the period, not bunched: " + distinct.get());

        String key = APP + "\u0000CL7\u0000" + SWEEP;
        long off = CronGrid.spreadOffsetSeconds(every5, ZoneOffset.UTC, key);
        Instant base = Instant.parse("2026-10-09T09:05:00Z");

        // Just after the shifted tick: that tick is current. Just before: the previous one.
        assertEquals(base.plusSeconds(off), CronGrid.latestSpreadTick(every5, ZoneOffset.UTC,
                base.plusSeconds(off + 1), Duration.ofHours(1), key));
        assertEquals(base.minusSeconds(300).plusSeconds(off), CronGrid.latestSpreadTick(every5, ZoneOffset.UTC,
                base.plusSeconds(off).minusSeconds(1), Duration.ofHours(1), key));

        // Two instances a few seconds apart agree on the tick.
        assertEquals(CronGrid.latestSpreadTick(every5, ZoneOffset.UTC, base.plusSeconds(off + 2),
                Duration.ofHours(1), key),
                CronGrid.latestSpreadTick(every5, ZoneOffset.UTC, base.plusSeconds(off + 40), Duration.ofHours(1),
                        key));
    }

    @Test
    void aSpreadScheduleClaimsOnTheShiftedTick() {

        EventSchedule spread = new EventSchedule().setCron("*/5 * * * *").setPerClient(true).setSpread(true);
        this.writeAccess.put(APP, List.of("FIN"));
        this.storeDefinition("SYSTEM", SWEEP, spread);
        this.definitions.put("FIN", effective(SWEEP, spread));

        long off = CronGrid.spreadOffsetSeconds(CronGrid.parse("*/5 * * * *"), ZoneOffset.UTC,
                APP + "\u0000FIN\u0000" + SWEEP);
        Instance a = this.inProcess("a", 2, Duration.ofMillis(5));

        this.clock.set(Instant.parse("2026-10-09T09:05:00Z").plusSeconds(off + 3));
        a.scheduler().pass().block();
        awaitIdle(a);

        List<ScheduleRun> rows = this.runs("FIN", SWEEP);
        assertEquals(1, rows.size());
        assertEquals(Instant.parse("2026-10-09T09:05:00Z").plusSeconds(off), rows.getFirst().getTickAt());
    }
}
