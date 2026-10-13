package com.fincity.saas.commons.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import com.fincity.saas.commons.core.document.EventDefinition;
import com.fincity.saas.commons.core.model.ClientSchedule;
import com.fincity.saas.commons.core.model.EventSchedule;
import com.fincity.saas.commons.core.model.ScheduleModel;
import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.function.Tuples;

/**
 * The Clients tab's view of one event: who is listed, in what order, and whether
 * the scheduler really runs each of them, which has to match EventScheduler.
 */
class EventScheduleViewServiceTest {

    private static final String APP = "leadzumptwo";
    private static final String EVENT = "LZ_WA_OUTBOX";

    private EventDefinitionService ed;
    private EventRunService runs;
    private IFeignSecurityService security;
    private EventScheduleViewService service;

    @BeforeEach
    void setUp() {
        this.ed = mock(EventDefinitionService.class);
        this.runs = mock(EventRunService.class);
        this.security = mock(IFeignSecurityService.class);
        this.service = new EventScheduleViewService(this.ed, this.runs, this.security);

        when(this.runs.latestRuns(eq(APP), eq(EVENT), any())).thenReturn(Mono.just(Map.of()));
        when(this.security.getClientByCode(anyString()))
                .thenAnswer(inv -> Mono.just(new Client().setId(BigInteger.ONE).setCode(inv.getArgument(0))
                        .setName("Client " + inv.getArgument(0))));
        when(this.ed.readDraftWithVersion(anyString())).thenAnswer(inv -> Mono.just(Tuples.of(new EventDefinition(), 0)));
    }

    private static EventDefinition copy(String id, String client, String baseClient, EventSchedule schedule) {
        EventDefinition d = new EventDefinition();
        d.setId(id);
        d.setClientCode(client);
        d.setBaseClientCode(baseClient);
        d.setAppCode(APP);
        d.setName(EVENT);
        d.setSchedule(schedule);
        return d;
    }

    private static EventDefinition effective(EventSchedule s) {
        EventDefinition d = new EventDefinition();
        d.setSchedule(s);
        return d;
    }

    private void listed(List<EventDefinition> copies, List<String> writers, List<String> visible) {
        when(this.ed.readAllCopies(EVENT, APP)).thenReturn(Flux.fromIterable(copies));
        when(this.security.getWriteAccessClientCodes(APP)).thenReturn(Mono.just(writers));
        when(this.runs.visibleAmong(any(), eq(APP), any(), eq(false))).thenReturn(Mono.just(visible));
    }

    private Mono<List<ClientSchedule>> asSystem(Mono<Page<ClientSchedule>> call) {
        ContextAuthentication ca = mock(ContextAuthentication.class);
        when(ca.getClientCode()).thenReturn("SYSTEM");
        return call.map(Page::getContent)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(new SecurityContextImpl(ca))));
    }

    @Test
    void perClientScheduleShowsEachClientsOwnState() {
        EventSchedule base = new EventSchedule().setCron("0 * * * * ?").setTimezone("UTC").setPerClient(true)
                .setEnabled(false);
        this.listed(List.of(copy("b", "SYSTEM", null, base), copy("f", "FIN", "SYSTEM", new EventSchedule().setEnabled(true))),
                List.of("SYSTEM", "FIN", "FIN2", "OTHER"), List.of("FIN2", "FIN", "SYSTEM"));

        when(this.ed.readEffective(EVENT, APP, "SYSTEM")).thenReturn(Mono.just(effective(base)));
        when(this.ed.readEffective(EVENT, APP, "FIN"))
                .thenReturn(Mono.just(effective(new EventSchedule(base).setEnabled(true))));
        when(this.ed.readEffective(EVENT, APP, "FIN2")).thenReturn(Mono.just(effective(base)));

        StepVerifier.create(this.asSystem(this.service.clientSchedules(APP, EVENT, "Asia/Kolkata", null, PageRequest.of(0, 20))))
                .assertNext(rows -> {
                    // Owner first, then the caller (here the same), then by code.
                    assertEquals(List.of("SYSTEM", "FIN", "FIN2"), rows.stream().map(ClientSchedule::getClientCode).toList());

                    ClientSchedule system = rows.get(0);
                    assertTrue(system.isOwner());
                    assertEquals(ClientSchedule.State.PAUSED, system.getState());

                    ClientSchedule fin = rows.get(1);
                    assertFalse(fin.isOwner());
                    assertEquals("f", fin.getCopyId());
                    assertEquals("b", fin.getBaseId());
                    assertEquals(Boolean.TRUE, fin.getOwnSchedule().getEnabled());
                    assertEquals(ClientSchedule.State.RUNS, fin.getState());
                    assertEquals(3, fin.getNextRuns().size());
                    assertEquals("Every minute", fin.getDescription());
                    assertEquals("Client FIN", fin.getClientName());

                    ClientSchedule fin2 = rows.get(2);
                    assertNull(fin2.getCopyId());
                    assertEquals(ClientSchedule.State.PAUSED, fin2.getState());
                })
                .verifyComplete();
    }

    @Test
    void notPerClientRunsOnlyForClientsWhoseCopySetsASchedule() {
        EventSchedule base = new EventSchedule().setCron("0 9 * * *").setTimezone("UTC").setPerClient(false);
        this.listed(List.of(copy("b", "SYSTEM", null, base), copy("f", "FIN", "SYSTEM", null)),
                List.of("FIN2"), List.of("SYSTEM", "FIN", "FIN2"));
        when(this.ed.readEffective(anyString(), anyString(), anyString())).thenReturn(Mono.just(effective(base)));

        StepVerifier.create(this.asSystem(this.service.clientSchedules(APP, EVENT, "UTC", null, PageRequest.of(0, 20))))
                .assertNext(rows -> {
                    assertEquals(ClientSchedule.State.RUNS, rows.get(0).getState());
                    // FIN has a copy, but it stores no schedule: the scheduler does not count it.
                    assertEquals(ClientSchedule.State.NOT_RUNNING, rows.get(1).getState());
                    assertEquals(ClientSchedule.State.NOT_RUNNING, rows.get(2).getState());
                })
                .verifyComplete();
    }

    @Test
    void perClientWithoutWriteAccessDoesNotRun() {
        EventSchedule base = new EventSchedule().setCron("0 9 * * *").setPerClient(true);
        this.listed(List.of(copy("b", "SYSTEM", null, base), copy("x", "GONE", "SYSTEM", new EventSchedule().setCron("0 10 * * *"))),
                List.of("SYSTEM"), List.of("SYSTEM", "GONE"));
        when(this.ed.readEffective(anyString(), anyString(), anyString())).thenReturn(Mono.just(effective(base)));

        StepVerifier.create(this.asSystem(this.service.clientSchedules(APP, EVENT, "UTC", null, PageRequest.of(0, 20))))
                .assertNext(rows -> assertEquals(ClientSchedule.State.NOT_RUNNING, rows.get(1).getState()))
                .verifyComplete();
    }

    @Test
    void anUnreadableCronOrNoCronSaysSo() {
        this.listed(List.of(copy("b", "SYSTEM", null, new EventSchedule().setCron("61 * * * *"))), List.of(),
                List.of("SYSTEM", "FIN"));
        when(this.ed.readEffective(EVENT, APP, "SYSTEM"))
                .thenReturn(Mono.just(effective(new EventSchedule().setCron("61 * * * *"))));
        when(this.ed.readEffective(EVENT, APP, "FIN")).thenReturn(Mono.just(effective(null)));

        StepVerifier.create(this.asSystem(this.service.clientSchedules(APP, EVENT, "UTC", null, PageRequest.of(0, 20))))
                .assertNext(rows -> {
                    assertEquals(ClientSchedule.State.INVALID, rows.get(0).getState());
                    assertEquals(ClientSchedule.State.NOT_SCHEDULED, rows.get(1).getState());
                })
                .verifyComplete();
    }

    @Test
    void aPendingDraftIsReported() {
        EventSchedule base = new EventSchedule().setCron("0 9 * * *").setPerClient(true);
        this.listed(List.of(copy("b", "SYSTEM", null, base), copy("f", "FIN", "SYSTEM", new EventSchedule().setEnabled(true))),
                List.of("SYSTEM", "FIN"), List.of("FIN"));
        when(this.ed.readEffective(anyString(), anyString(), anyString())).thenReturn(Mono.just(effective(base)));
        when(this.ed.readDraftWithVersion("f"))
                .thenReturn(Mono.just(Tuples.of(effective(new EventSchedule(base).setEnabled(false)), 4)));

        StepVerifier.create(this.asSystem(this.service.clientSchedules(APP, EVENT, "UTC", null, PageRequest.of(0, 20))))
                .assertNext(rows -> {
                    assertEquals(4, rows.getFirst().getDraftVersion());
                    assertEquals(Boolean.FALSE, rows.getFirst().getDraftSchedule().getEnabled());
                })
                .verifyComplete();
    }

    @Test
    void onlyTheRequestedPageIsBuiltAndAClientCanBePicked() {
        EventSchedule base = new EventSchedule().setCron("0 9 * * *").setPerClient(true);
        this.listed(List.of(copy("b", "SYSTEM", null, base)), List.of("A1", "A2", "A3", "A4"),
                List.of("A4", "A3", "SYSTEM", "A2", "A1"));
        when(this.ed.readEffective(anyString(), anyString(), anyString())).thenReturn(Mono.just(effective(base)));

        ContextAuthentication ca = mock(ContextAuthentication.class);
        when(ca.getClientCode()).thenReturn("SYSTEM");

        StepVerifier.create(this.service.clientSchedules(APP, EVENT, "UTC", null, PageRequest.of(1, 2))
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(new SecurityContextImpl(ca)))))
                .assertNext(page -> {
                    assertEquals(5, page.getTotalElements());
                    // SYSTEM (has a copy), then A1..A4 by code: page 1 of size 2 is A2, A3.
                    assertEquals(List.of("A2", "A3"), page.getContent().stream().map(ClientSchedule::getClientCode).toList());
                })
                .verifyComplete();

        StepVerifier.create(this.service.clientSchedules(APP, EVENT, "UTC", "A4", PageRequest.of(0, 20))
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(new SecurityContextImpl(ca)))))
                .assertNext(page -> assertEquals(List.of("A4"),
                        page.getContent().stream().map(ClientSchedule::getClientCode).toList()))
                .verifyComplete();
    }

    @Test
    void theBuilderRoundTripsThroughTheService() {
        ScheduleModel weekdays = new ScheduleModel().setFrequency("weekly").setHour(9).setMinute(0)
                .setDays(List.of(1, 2, 3, 4, 5)).setEvery(5).setDayOfMonth("1").setCron("");

        StepVerifier.create(this.service.toCron(weekdays, "Asia/Kolkata"))
                .assertNext(p -> {
                    assertEquals("30 3 * * 1,2,3,4,5", p.getCron());
                    assertEquals("UTC", p.getTimezone());
                    assertTrue(p.isValid());
                    assertEquals(3, p.getNextRuns().size());
                    assertEquals("Weekdays at 09:00 (Asia/Kolkata)", p.getDescription());
                })
                .verifyComplete();

        StepVerifier.create(this.service.fromCron("61 * * * *", null, "UTC"))
                .assertNext(p -> {
                    assertFalse(p.isValid());
                    assertTrue(p.getError().startsWith("The scheduler cannot read this cron"));
                })
                .verifyComplete();
    }
}
