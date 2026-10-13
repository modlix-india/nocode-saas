package com.fincity.saas.commons.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import com.fincity.saas.commons.core.dao.EventRunDAO;
import com.fincity.saas.commons.core.document.EventDefinition;
import com.fincity.saas.commons.core.dto.EventRun;
import com.fincity.saas.commons.core.jooq.enums.CoreEventRunsStatus;
import com.fincity.saas.commons.core.jooq.enums.CoreEventRunsTriggerType;
import com.fincity.saas.commons.core.model.EventRunTask;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class EventRunServiceTest {

    private static final String APP = "leadzump";

    private EventRunDAO dao;
    private EventDefinitionService edService;
    private IFeignSecurityService security;
    private EventRunService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        this.dao = mock(EventRunDAO.class);
        this.edService = mock(EventDefinitionService.class);
        this.security = mock(IFeignSecurityService.class);

        // throwMessage answers with the exception its function builds, the way the real one does.
        CoreMessageResourceService messages = mock(CoreMessageResourceService.class, inv -> {
            if ("throwMessage".equals(inv.getMethod().getName()))
                return Mono.error(((Function<String, GenericException>) inv.getArgument(0)).apply(inv.getArgument(1)));
            return null;
        });

        this.service = new EventRunService(this.dao, this.edService, this.security, messages);
    }

    // ---------------------------------------------------------------- describe

    @Test
    void anEventWithoutAMarkerIsAPlainEvent() {
        EventRun run = EventRunService.describe(event("Storage.Lead.Create", Map.of("dataObject", Map.of())));
        assertEquals(CoreEventRunsTriggerType.EVENT, run.getTriggerType());
        assertNull(run.getScheduleRunId());
    }

    @Test
    void aCronTickCarriesItsRunAndTick() {
        EventRun run = EventRunService.describe(event("LZ_WA_OUTBOX", Map.of("_schedule",
                Map.of("kind", "cron", "runId", "r-1", "tickAt", "2026-10-13T03:30:00Z"))));
        assertEquals(CoreEventRunsTriggerType.SCHEDULE, run.getTriggerType());
        assertEquals("r-1", run.getScheduleRunId());
        assertEquals(LocalDateTime.of(2026, 10, 13, 3, 30), run.getScheduledFor());
    }

    @Test
    void aOneOffCarriesItsAttempt() {
        EventRun run = EventRunService.describe(event("REMIND", Map.of("_schedule",
                Map.of("kind", "once", "id", "o-9", "attempt", 3, "runAt", "2026-10-13T10:00:00Z"))));
        assertEquals(CoreEventRunsTriggerType.ONE_OFF, run.getTriggerType());
        assertEquals("o-9", run.getScheduleRunId());
        assertEquals(3, run.getAttempt());
    }

    @Test
    void errorsNameTheRootCause() {
        String msg = EventRunService.errorOf(new IllegalStateException("Task failed",
                new IllegalArgumentException("Template welcome not found")));
        assertEquals("Task failed (cause: Template welcome not found)", msg);
        assertEquals("NullPointerException", EventRunService.errorOf(new NullPointerException()));
    }

    // ---------------------------------------------------------------- writing

    @Test
    void finishCountsFailedTasksAndSummarises() {
        when(this.dao.finish(any(), any(), any(), anyInt(), any(), any(), anyLong())).thenReturn(Mono.just(true));

        List<EventRunTask> tasks = List.of(
                new EventRunTask().setKey("a").setStatus(EventRunTask.Status.SUCCESS),
                new EventRunTask().setKey("b").setStatus(EventRunTask.Status.FAILED).setError("boom"));

        StepVerifier.create(this.service.finish(Optional.of(ULong.valueOf(7)), System.currentTimeMillis(), false,
                tasks, null)).verifyComplete();

        verify(this.dao).finish(eq(ULong.valueOf(7)), eq(CoreEventRunsStatus.FAILED), eq(tasks), eq(1),
                eq("1 task failed"), any(), anyLong());
    }

    @Test
    void aFailedWriteNeverFailsTheRun() {
        when(this.dao.insert(any())).thenReturn(Mono.error(new IllegalStateException("db down")));

        StepVerifier.create(this.service.start(event("X", Map.of()), 2))
                .expectNext(Optional.empty())
                .verifyComplete();

        StepVerifier.create(this.service.record(event("X", Map.of()), CoreEventRunsStatus.SKIPPED, "s"))
                .verifyComplete();
    }

    @Test
    void finishWithoutARowDoesNothing() {
        StepVerifier.create(this.service.finish(Optional.empty(), 0L, true, List.of(), null)).verifyComplete();
        verify(this.dao, never()).finish(any(), any(), any(), anyInt(), any(), any(), anyLong());
    }

    // ---------------------------------------------------------------- no action

    @Test
    void noActionIsRecordedForAScheduledEvent() {
        when(this.dao.insert(any())).thenReturn(Mono.just(ULong.valueOf(1)));

        StepVerifier.create(this.service.recordNoAction(event("JOB",
                Map.of("_schedule", Map.of("kind", "cron", "runId", "r", "tickAt", "2026-10-13T00:00:00Z")))))
                .verifyComplete();

        ArgumentCaptor<EventRun> row = ArgumentCaptor.forClass(EventRun.class);
        verify(this.dao).insert(row.capture());
        assertEquals(CoreEventRunsStatus.NO_ACTION, row.getValue().getStatus());
        assertTrue(row.getValue().getError().contains("JOB"));
        verify(this.edService, never()).readEffective(anyString(), anyString(), anyString());
    }

    @Test
    void noActionIsRecordedForAHandOff() {
        when(this.dao.insert(any())).thenReturn(Mono.just(ULong.valueOf(1)));
        StepVerifier.create(this.service.recordNoAction(event("WHATSAPP_INBOUND", Map.of()))).verifyComplete();
        verify(this.dao).insert(any());
    }

    @Test
    void noActionIsRecordedWhenTheAppDefinesTheEvent() {
        when(this.dao.insert(any())).thenReturn(Mono.just(ULong.valueOf(1)));
        when(this.edService.readEffective("Storage.Lead.Create", APP, "FIN"))
                .thenReturn(Mono.just(new EventDefinition()));

        StepVerifier.create(this.service.recordNoAction(event("Storage.Lead.Create", Map.of()))).verifyComplete();
        verify(this.dao).insert(any());
    }

    @Test
    void anUndefinedPlatformEventIsNotRecorded() {
        when(this.edService.readEffective("USER_REGISTERED", APP, "FIN")).thenReturn(Mono.empty());

        StepVerifier.create(this.service.recordNoAction(event("USER_REGISTERED", Map.of()))).verifyComplete();
        verify(this.dao, never()).insert(any());
    }

    // ---------------------------------------------------------------- reading

    @Test
    void readsItsOwnClientWithoutAskingSecurity() {
        when(this.dao.readPage(eq(APP), eq(List.of("FIN")), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(new PageImpl<>(List.of(new EventRun()))));

        StepVerifier.create(this.asCaller("FIN", this.service.readPage(APP, null, false, null, null, null, null, null,
                        PageRequest.of(0, 20))))
                .expectNextMatches(p -> p.getTotalElements() == 1)
                .verifyComplete();

        verify(this.security, never()).doesClientManageClientCode(anyString(), anyString());
    }

    @Test
    void readsAManagedClientWhenItsOwnClientMayReadTheApp() {
        when(this.security.doesClientManageClientCode("SYSTEM", "FIN")).thenReturn(Mono.just(true));
        when(this.security.hasReadAccess(APP, "SYSTEM")).thenReturn(Mono.just(true));
        when(this.dao.readPage(eq(APP), eq(List.of("FIN")), any(), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(Page.empty()));

        StepVerifier.create(this.asCaller("SYSTEM", this.service.readPage(APP, "FIN", false, null, null, null, null, null,
                        PageRequest.of(0, 20))))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void aClientItDoesNotManageIsForbidden() {
        when(this.security.doesClientManageClientCode("FIN", "OTHER")).thenReturn(Mono.just(false));

        StepVerifier.create(this.asCaller("FIN", this.service.readPage(APP, "OTHER", false, null, null, null, null, null,
                        PageRequest.of(0, 20))))
                .expectErrorMatches(e -> e instanceof GenericException g && g.getStatusCode() == HttpStatus.FORBIDDEN)
                .verify();

        verify(this.dao, never()).readPage(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void aManagedClientIsForbiddenWithoutAppAccess() {
        when(this.security.doesClientManageClientCode("SYSTEM", "FIN")).thenReturn(Mono.just(true));
        when(this.security.hasReadAccess(APP, "SYSTEM")).thenReturn(Mono.just(false));

        StepVerifier.create(this.asCaller("SYSTEM", this.service.readPage(APP, "FIN", false, null, null, null, null, null,
                        PageRequest.of(0, 20))))
                .expectErrorMatches(e -> e instanceof GenericException g && g.getStatusCode() == HttpStatus.FORBIDDEN)
                .verify();
    }

    @Test
    void aRunOfAnotherClientIsForbiddenById() {
        when(this.dao.read(ULong.valueOf(5)))
                .thenReturn(Mono.just(new EventRun().setAppCode(APP).setClientCode("OTHER")));
        when(this.security.doesClientManageClientCode("FIN", "OTHER")).thenReturn(Mono.just(false));

        StepVerifier.create(this.asCaller("FIN", this.service.read(ULong.valueOf(5))))
                .expectErrorMatches(e -> e instanceof GenericException g && g.getStatusCode() == HttpStatus.FORBIDDEN)
                .verify();
    }

    @Test
    void anAppCodeIsRequired() {
        StepVerifier.create(this.asCaller("FIN", this.service.readPage(" ", null, false, null, null, null, null, null,
                        PageRequest.of(0, 20))))
                .expectErrorMatches(e -> e instanceof GenericException g && g.getStatusCode() == HttpStatus.BAD_REQUEST)
                .verify();
    }

    @Test
    void allClientsReadsOwnPlusTheManagedClientsThatHaveRuns() {
        when(this.dao.distinctClients(APP, "JOB", EventRunService.MAX_CANDIDATE_CLIENTS))
                .thenReturn(Mono.just(List.of("FIN", "FIN2", "OTHER", "SYSTEM")));
        when(this.security.hasReadAccess(APP, "SYSTEM")).thenReturn(Mono.just(true));
        when(this.security.filterManagedClientCodes("SYSTEM", List.of("FIN", "FIN2", "OTHER")))
                .thenReturn(Mono.just(List.of("FIN", "FIN2")));
        when(this.dao.readPage(eq(APP), any(), eq("JOB"), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(Page.empty()));

        StepVerifier.create(this.asCaller("SYSTEM", this.service.readPage(APP, null, true, "JOB", null, null, null,
                        null, PageRequest.of(0, 20))))
                .expectNextCount(1)
                .verifyComplete();

        verify(this.dao).readPage(eq(APP), eq(List.of("SYSTEM", "FIN", "FIN2")), eq("JOB"), any(), any(), any(),
                any(), any());
    }

    @Test
    void allClientsWithoutAppAccessIsOnlyYourOwn() {
        when(this.dao.distinctClients(APP, "JOB", EventRunService.MAX_CANDIDATE_CLIENTS))
                .thenReturn(Mono.just(List.of("FIN", "OTHER")));
        when(this.security.hasReadAccess(APP, "FIN")).thenReturn(Mono.just(false));
        when(this.dao.readPage(eq(APP), any(), eq("JOB"), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(Page.empty()));

        StepVerifier.create(this.asCaller("FIN", this.service.readPage(APP, null, true, "JOB", null, null, null,
                        null, PageRequest.of(0, 20))))
                .expectNextCount(1)
                .verifyComplete();

        verify(this.dao).readPage(eq(APP), eq(List.of("FIN")), eq("JOB"), any(), any(), any(), any(), any());
        verify(this.security, never()).filterManagedClientCodes(anyString(), any());
    }

    // ---------------------------------------------------------------- cleanup

    @Test
    void successesGoFirstOnTheirShorterRetentionThenEverythingOld() {
        when(this.dao.failRunningStartedBefore(any(), anyString(), any())).thenReturn(Mono.just(2));
        when(this.dao.deleteStartedBefore(any(), eq(CoreEventRunsStatus.SUCCESS), anyInt()))
                .thenReturn(Mono.just(5000), Mono.just(300));
        when(this.dao.deleteStartedBefore(any(), isNull(), anyInt())).thenReturn(Mono.just(12));

        StepVerifier.create(this.service.cleanupOlderThan(90, 7, 6, 50_000))
                .expectNext(Map.of("runsRemoved", 5312, "successesRemoved", 5300, "staleClosed", 2))
                .verifyComplete();

        ArgumentCaptor<LocalDateTime> successBefore = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(this.dao, times(2)).deleteStartedBefore(successBefore.capture(), eq(CoreEventRunsStatus.SUCCESS),
                eq(5000));
        assertAbout(successBefore.getValue(), EventRunService.now().minusDays(7));

        ArgumentCaptor<LocalDateTime> allBefore = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(this.dao).deleteStartedBefore(allBefore.capture(), isNull(), eq(5000));
        assertAbout(allBefore.getValue(), EventRunService.now().minusDays(90));

        ArgumentCaptor<LocalDateTime> staleBefore = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(this.dao).failRunningStartedBefore(staleBefore.capture(), anyString(), any());
        assertAbout(staleBefore.getValue(), EventRunService.now().minusHours(6));
    }

    @Test
    void oneLimitCoversBothKinds() {
        when(this.dao.failRunningStartedBefore(any(), anyString(), any())).thenReturn(Mono.just(0));
        when(this.dao.deleteStartedBefore(any(), any(), anyInt())).thenAnswer(inv -> Mono.just((int) inv.getArgument(2)));

        StepVerifier.create(this.service.cleanupOlderThan(90, 7, 6, 7000))
                .expectNext(Map.of("runsRemoved", 7000, "successesRemoved", 7000, "staleClosed", 0))
                .verifyComplete();

        // 5000 then the 2000 left under the limit, all spent on successes: nothing left for the rest.
        verify(this.dao).deleteStartedBefore(any(), eq(CoreEventRunsStatus.SUCCESS), eq(5000));
        verify(this.dao).deleteStartedBefore(any(), eq(CoreEventRunsStatus.SUCCESS), eq(2000));
        verify(this.dao, never()).deleteStartedBefore(any(), isNull(), anyInt());
    }

    @Test
    void anyCallerIsHeldToTheFloorsAndTheCap() {
        // The endpoint is unauthenticated: asking for 0 everywhere and a huge limit must not delete
        // a success younger than a day or any run younger than 30 days, or call anything stale
        // before an hour.
        when(this.dao.failRunningStartedBefore(any(), anyString(), any())).thenReturn(Mono.just(0));
        when(this.dao.deleteStartedBefore(any(), any(), anyInt())).thenReturn(Mono.just(0));

        StepVerifier.create(this.service.cleanupOlderThan(0, 0, 0, Integer.MAX_VALUE)).expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<LocalDateTime> successBefore = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(this.dao).deleteStartedBefore(successBefore.capture(), eq(CoreEventRunsStatus.SUCCESS), anyInt());
        assertAbout(successBefore.getValue(),
                EventRunService.now().minusDays(EventRunService.MIN_SUCCESS_RETENTION_DAYS));

        ArgumentCaptor<LocalDateTime> allBefore = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(this.dao).deleteStartedBefore(allBefore.capture(), isNull(), anyInt());
        assertAbout(allBefore.getValue(), EventRunService.now().minusDays(EventRunService.MIN_RETENTION_DAYS));

        ArgumentCaptor<LocalDateTime> staleBefore = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(this.dao).failRunningStartedBefore(staleBefore.capture(), anyString(), any());
        assertAbout(staleBefore.getValue(), EventRunService.now().minusHours(EventRunService.MIN_STALE_HOURS));
    }

    @Test
    void successesNeverOutliveTheOverallRetention() {
        when(this.dao.failRunningStartedBefore(any(), anyString(), any())).thenReturn(Mono.just(0));
        when(this.dao.deleteStartedBefore(any(), any(), anyInt())).thenReturn(Mono.just(0));

        StepVerifier.create(this.service.cleanupOlderThan(60, 365, 6, 1000)).expectNextCount(1).verifyComplete();

        ArgumentCaptor<LocalDateTime> successBefore = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(this.dao).deleteStartedBefore(successBefore.capture(), eq(CoreEventRunsStatus.SUCCESS), anyInt());
        assertAbout(successBefore.getValue(), EventRunService.now().minusDays(60));
    }

    // ---------------------------------------------------------------- no-action throttle

    @Test
    void noActionIsWrittenOnceAnHourAndTheNextRowCountsTheRest() {
        when(this.dao.insert(any())).thenReturn(Mono.just(ULong.valueOf(1)));
        when(this.edService.readEffective("Storage.Lead.Create", APP, "FIN"))
                .thenReturn(Mono.just(new EventDefinition()));

        EventQueObject e = event("Storage.Lead.Create", Map.of());
        long t0 = 1_000_000L;

        StepVerifier.create(this.service.recordNoAction(e, t0)).verifyComplete();
        for (int i = 1; i <= 3; i++)
            StepVerifier.create(this.service.recordNoAction(e, t0 + i * 60_000L)).verifyComplete();

        verify(this.dao, times(1)).insert(any());

        StepVerifier.create(this.service.recordNoAction(e, t0 + EventRunService.NO_ACTION_WINDOW_MS))
                .verifyComplete();

        ArgumentCaptor<EventRun> rows = ArgumentCaptor.forClass(EventRun.class);
        verify(this.dao, times(2)).insert(rows.capture());
        assertTrue(rows.getAllValues().get(1).getError().contains("3 more times"),
                rows.getAllValues().get(1).getError());
    }

    @Test
    void theThrottleIsPerClientAndPerEvent() {
        when(this.dao.insert(any())).thenReturn(Mono.just(ULong.valueOf(1)));
        when(this.edService.readEffective(anyString(), eq(APP), anyString()))
                .thenReturn(Mono.just(new EventDefinition()));

        StepVerifier.create(this.service.recordNoAction(event("A", Map.of()), 0)).verifyComplete();
        StepVerifier.create(this.service.recordNoAction(event("B", Map.of()), 0)).verifyComplete();
        StepVerifier.create(this.service.recordNoAction(event("A", Map.of()).setClientCode("FIN2"), 0))
                .verifyComplete();

        verify(this.dao, times(3)).insert(any());
    }

    @Test
    void scheduledNoActionIsThrottledToo() {
        when(this.dao.insert(any())).thenReturn(Mono.just(ULong.valueOf(1)));
        EventQueObject tick = event("JOB",
                Map.of("_schedule", Map.of("kind", "cron", "runId", "r", "tickAt", "2026-10-13T00:00:00Z")));

        StepVerifier.create(this.service.recordNoAction(tick, 0)).verifyComplete();
        StepVerifier.create(this.service.recordNoAction(tick, 60_000)).verifyComplete();

        verify(this.dao, times(1)).insert(any());
    }

    @Test
    void handOffsAreNeverThrottled() {
        when(this.dao.insert(any())).thenReturn(Mono.just(ULong.valueOf(1)));
        EventQueObject inbound = event("WHATSAPP_INBOUND", Map.of());

        for (int i = 0; i < 5; i++)
            StepVerifier.create(this.service.recordNoAction(inbound, i)).verifyComplete();

        verify(this.dao, times(5)).insert(any());
    }

    private static void assertAbout(LocalDateTime actual, LocalDateTime expected) {
        assertTrue(Math.abs(java.time.Duration.between(actual, expected).toSeconds()) < 5,
                "expected about " + expected + ", was " + actual);
    }

    // ---------------------------------------------------------------- helpers

    private static EventQueObject event(String name, Map<String, Object> data) {
        return new EventQueObject().setAppCode(APP).setClientCode("FIN").setEventName(name).setData(data);
    }

    private <T> Mono<T> asCaller(String clientCode, Mono<T> call) {
        ContextAuthentication ca = mock(ContextAuthentication.class);
        when(ca.getClientCode()).thenReturn(clientCode);
        return call.contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(new SecurityContextImpl(ca))));
    }
}
