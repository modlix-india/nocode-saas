package com.fincity.saas.commons.core.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.fincity.saas.commons.core.model.EventSchedule;
import com.fincity.saas.commons.mq.events.EventQueObject;

import reactor.core.publisher.Mono;

/**
 * A schedule does nothing for a client that security reports as deleted, inactive
 * or locked, or as managed by a client that is: not the client holding a
 * schedule, not a client a perClient schedule fans out to, and not its one-offs.
 */
class EventSchedulerInactiveClientTest extends AbstractSchedulerMongoTest {

    private static final String SWEEP = "outboxSweep";
    private static final String REMIND = "taskReminder";

    private static EventSchedule everyMinute(boolean perClient) {
        return new EventSchedule().setCron("* * * * *").setPerClient(perClient);
    }

    private ScheduledEvent row(String key) {
        return mongo().findOne(new Query(Criteria.where("key").is(key)), ScheduledEvent.class).block();
    }

    @Test
    void anInactiveClientHoldingASingleRunScheduleRunsNothing() {

        EventSchedule schedule = everyMinute(false);
        this.storeDefinition("FIN", SWEEP, schedule);
        this.storeDefinition("GONE", SWEEP, schedule);
        this.definitions.put("FIN", effective(SWEEP, schedule));
        this.definitions.put("GONE", effective(SWEEP, schedule));
        this.notActive.add("GONE");

        this.instance().scheduler().pass().block();

        assertEquals(1, this.events.of("FIN", SWEEP).size());
        assertEquals(0, this.events.of("GONE", SWEEP).size());
        assertEquals(List.of(), this.runs("GONE", SWEEP));
    }

    @Test
    void aPerClientScheduleSkipsAWriterThatIsNotActive() {

        // Security's write-access list already drops deleted and inactive clients, but not
        // a locked one or one whose managing client is inactive. The status check does.
        this.writeAccess.put(APP, List.of("FIN", "LOCKED"));
        this.storeDefinition("SYSTEM", SWEEP, everyMinute(true));
        this.definitions.put("FIN", effective(SWEEP, everyMinute(true)));
        this.definitions.put("LOCKED", effective(SWEEP, everyMinute(true)));
        this.notActive.add("LOCKED");

        this.instance().scheduler().pass().block();

        assertEquals(1, this.events.of("FIN", SWEEP).size());
        assertEquals(0, this.events.of("LOCKED", SWEEP).size());
    }

    @Test
    void statusIsAskedOncePerRefreshAndAReactivatedClientRunsAgain() {

        EventSchedule schedule = everyMinute(false);
        this.storeDefinition("FIN", SWEEP, schedule);
        this.definitions.put("FIN", effective(SWEEP, schedule));
        this.notActive.add("FIN");

        Instance a = this.instance();
        a.scheduler().pass().block();
        this.clock.advanceSeconds(60);
        a.scheduler().pass().block();

        assertEquals(0, this.events.of("FIN", SWEEP).size());
        assertEquals(1, this.statusCalls.get(), "the answer is reused inside clientsRefreshSeconds");

        // Reactivated: picked up on the first pass after the answer goes stale.
        this.notActive.clear();
        this.clock.advanceSeconds(300);
        a.scheduler().pass().block();

        assertEquals(1, this.events.of("FIN", SWEEP).size());
        assertEquals(2, this.statusCalls.get());
    }

    @Test
    void whenSecurityHasNeverAnsweredTheScheduleRunsAsBefore() {

        EventSchedule schedule = everyMinute(false);
        this.storeDefinition("FIN", SWEEP, schedule);
        this.definitions.put("FIN", effective(SWEEP, schedule));
        this.notActive.add("FIN");
        this.statusDown = true;

        this.instance().scheduler().pass().block();

        assertEquals(1, this.events.of("FIN", SWEEP).size(),
                "only a client security has reported as not active is left out");
    }

    @Test
    void whenSecurityGoesDownTheLastAnswerKeepsServing() {

        EventSchedule schedule = everyMinute(false);
        this.storeDefinition("FIN", SWEEP, schedule);
        this.definitions.put("FIN", effective(SWEEP, schedule));
        this.notActive.add("FIN");

        Instance a = this.instance();
        a.scheduler().pass().block();

        this.statusDown = true;
        this.clock.advanceSeconds(600);
        a.scheduler().pass().block();

        assertEquals(0, this.events.of("FIN", SWEEP).size());
    }

    @Test
    void aOneOffForAnInactiveClientIsClosedWithTheReasonAndNotRaised() {

        Instance a = this.instance();
        a.oneOffs().schedule(APP, "GONE", false, REMIND, Map.of(), this.clock.instant(), "r-1", "110").block();
        a.oneOffs().schedule(APP, "FIN", false, REMIND, Map.of(), this.clock.instant(), "r-2", "110").block();
        this.notActive.add("GONE");

        a.scheduler().pass().block();

        assertEquals(0, this.events.of("GONE", REMIND).size());
        assertEquals(1, this.events.of("FIN", REMIND).size());

        ScheduledEvent closed = this.row("r-1");
        assertEquals(ScheduledEvent.Status.FAILED, closed.getStatus());
        assertTrue(closed.getLastError().contains("'GONE'"), closed.getLastError());

        this.clock.advanceSeconds(3600);
        a.scheduler().pass().block();
        assertEquals(0, this.events.of("GONE", REMIND).size(), "a closed one-off is never claimed again");
    }

    @Test
    void anInProcessOneOffForAnInactiveClientFreesItsSlotWithoutRunning() {

        List<EventQueObject> ran = new CopyOnWriteArrayList<>();
        Instance a = this.instance(this.settings().setRunConcurrency(1));
        a.scheduler().setRunner(q -> {
            ran.add(q);
            return Mono.just(true);
        });

        a.oneOffs().schedule(APP, "GONE", false, REMIND, Map.of(), this.clock.instant(), "r-1", "110").block();
        a.oneOffs().schedule(APP, "FIN", false, REMIND, Map.of(), this.clock.instant().plusSeconds(1), "r-2", "110")
                .block();
        this.notActive.add("GONE");

        this.clock.advanceSeconds(2);
        a.scheduler().pass().block();

        // One slot: the refused one-off gave it back, so the next one ran in the same pass.
        assertEquals(List.of("FIN"), ran.stream().map(EventQueObject::getClientCode).toList());
        assertEquals(ScheduledEvent.Status.FAILED, this.row("r-1").getStatus());
        assertEquals(0, a.scheduler().inFlight());
    }
}
