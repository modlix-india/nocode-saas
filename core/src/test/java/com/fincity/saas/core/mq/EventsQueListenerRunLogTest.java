package com.fincity.saas.core.mq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fincity.saas.commons.core.document.EventAction;
import com.fincity.saas.commons.core.enums.EventActionTaskType;
import com.fincity.saas.commons.core.jooq.enums.CoreEventRunsStatus;
import com.fincity.saas.commons.core.model.EventActionTask;
import com.fincity.saas.commons.core.model.EventRunTask;
import com.fincity.saas.commons.core.mq.services.EventCallFunctionService;
import com.fincity.saas.commons.core.mq.services.EventEmailService;
import com.fincity.saas.commons.core.scheduler.EventScheduler;
import com.fincity.saas.commons.core.service.EventActionService;
import com.fincity.saas.commons.core.service.EventRunService;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Every run of an event goes into the event run log: RUNNING before the first task, then
 * SUCCESS or FAILED with each task's own outcome. An event with nothing to run, or one that
 * cannot run at all, leaves a row too.
 */
class EventsQueListenerRunLogTest {

    private EventActionService actions;
    private EventCallFunctionService functions;
    private IFeignSecurityService security;
    private EventRunService runs;
    private EventsQueListener listener;

    private final EventQueObject message = new EventQueObject().setEventName("leadCreated")
            .setAppCode("leadzumptwo")
            .setClientCode("FIN");

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        this.actions = mock(EventActionService.class);
        this.functions = mock(EventCallFunctionService.class);
        this.security = mock(IFeignSecurityService.class);
        this.runs = mock(EventRunService.class);

        EventScheduler scheduler = mock(EventScheduler.class);
        when(scheduler.trackCompletion(any(), any())).thenAnswer(inv -> inv.getArgument(1));

        when(this.security.getClientByCode("FIN"))
                .thenReturn(Mono.just((Client) new Client().setId(BigInteger.ONE).setCode("FIN")));

        when(this.runs.start(any(), anyInt())).thenReturn(Mono.just(Optional.of(ULong.valueOf(42))));
        when(this.runs.finish(any(), anyLong(), any(Boolean.class), any(), any())).thenReturn(Mono.empty());
        when(this.runs.record(any(), any(), any())).thenReturn(Mono.empty());
        when(this.runs.recordNoAction(any())).thenReturn(Mono.empty());

        this.listener = new EventsQueListener(this.actions, this.functions, mock(EventEmailService.class),
                this.security, scheduler, this.runs);
        this.listener.init();
    }

    private void action(Map<String, EventActionTask> tasks) {
        when(this.actions.read("leadCreated", "leadzumptwo", "FIN"))
                .thenReturn(Mono.just(new ObjectWithUniqueID<>(new EventAction().setTasks(tasks), "x")));
    }

    private static EventActionTask fn(String key, int order) {
        return new EventActionTask().setKey(key).setOrder(order).setType(EventActionTaskType.CALL_CORE_FUNCTION)
                .setParameters(Map.of("name", key));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aRunIsOpenedThenClosedWithEachTasksOutcome() {

        Map<String, EventActionTask> tasks = new LinkedHashMap<>();
        tasks.put("notify", fn("notify", 2));
        tasks.put("store", fn("store", 1));
        this.action(tasks);

        when(this.functions.execute(any(), any(), any())).thenAnswer(inv -> {
            EventActionTask t = inv.getArgument(1);
            return "notify".equals(t.getKey()) ? Mono.error(new IllegalStateException("Template welcome not found"))
                    : Mono.just(true);
        });

        StepVerifier.create(this.listener.run(this.message)).expectNext(false).verifyComplete();

        verify(this.runs).start(this.message, 2);

        ArgumentCaptor<List<EventRunTask>> outcomes = ArgumentCaptor.forClass(List.class);
        verify(this.runs).finish(eq(Optional.of(ULong.valueOf(42))), anyLong(), eq(false), outcomes.capture(),
                eq(null));

        List<EventRunTask> ran = outcomes.getValue();
        assertEquals(2, ran.size());
        assertEquals("store", ran.get(0).getKey());
        assertEquals(EventRunTask.Status.SUCCESS, ran.get(0).getStatus());
        assertEquals("notify", ran.get(1).getKey());
        assertEquals(EventRunTask.Status.FAILED, ran.get(1).getStatus());
        assertTrue(ran.get(1).getError().contains("Template welcome not found"));
        assertEquals("CALL_CORE_FUNCTION", ran.get(1).getType());

        verify(this.runs, never()).record(any(), any(), any());
    }

    @Test
    void aFailedTaskKeepsItsStackTraceAndTheFunctionItCalled() {

        this.action(Map.of("one", new EventActionTask().setKey("one").setOrder(1)
                .setType(EventActionTaskType.CALL_CORE_FUNCTION)
                .setParameters(Map.of("namespace", "Leads", "name", "syncAll"))));
        // No message, like the ConcurrentModificationException a schedule run showed with nothing else.
        when(this.functions.execute(any(), any(), any()))
                .thenReturn(Mono.error(new java.util.ConcurrentModificationException()));

        StepVerifier.create(this.listener.run(this.message)).expectNext(false).verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EventRunTask>> outcomes = ArgumentCaptor.forClass(List.class);
        verify(this.runs).finish(any(), anyLong(), eq(false), outcomes.capture(), any());

        EventRunTask one = outcomes.getValue().get(0);
        assertEquals("Leads.syncAll", one.getTarget());
        assertTrue(one.getError().startsWith("ConcurrentModificationException in EventsQueListenerRunLogTest."),
                one.getError());
        assertTrue(one.getDetail().startsWith("java.util.ConcurrentModificationException\n"), one.getDetail());
        assertTrue(one.getDetail().contains("at com.fincity.saas.core.mq.EventsQueListenerRunLogTest."),
                one.getDetail());
    }

    @Test
    void aTaskReportingFalseIsAFailureWithAReason() {

        this.action(Map.of("only", fn("only", 1)));
        when(this.functions.execute(any(), any(), any())).thenReturn(Mono.just(false));

        StepVerifier.create(this.listener.run(this.message)).expectNext(false).verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EventRunTask>> outcomes = ArgumentCaptor.forClass(List.class);
        verify(this.runs).finish(any(), anyLong(), eq(false), outcomes.capture(), any());
        assertEquals(EventRunTask.Status.FAILED, outcomes.getValue().get(0).getStatus());
        assertTrue(outcomes.getValue().get(0).getError() != null);
    }

    @Test
    void anEventWithNoActionGoesToTheNoActionRule() {

        when(this.actions.read("leadCreated", "leadzumptwo", "FIN")).thenReturn(Mono.empty());

        StepVerifier.create(this.listener.run(this.message)).verifyComplete();

        verify(this.runs).recordNoAction(this.message);
        verify(this.runs, never()).start(any(), anyInt());
        verify(this.runs, never()).record(any(), any(), any());
    }

    @Test
    void anUnknownClientIsRecordedAsFailed() {

        when(this.security.getClientByCode("FIN")).thenReturn(Mono.empty());

        StepVerifier.create(this.listener.run(this.message)).verifyComplete();

        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(this.runs).record(eq(this.message), eq(CoreEventRunsStatus.FAILED), error.capture());
        assertTrue(error.getValue().contains("FIN"));
    }

    @Test
    void anErrorBeforeTheActionRunsIsRecordedAndStillThrown() {

        when(this.actions.read("leadCreated", "leadzumptwo", "FIN"))
                .thenReturn(Mono.error(new IllegalStateException("mongo unreachable")));

        StepVerifier.create(this.listener.run(this.message)).expectError(IllegalStateException.class).verify();

        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(this.runs).record(eq(this.message), eq(CoreEventRunsStatus.FAILED), error.capture());
        assertTrue(error.getValue().contains("mongo unreachable"));
    }

    @Test
    void aRunLogThatCannotBeWrittenDoesNotStopTheTasks() {

        this.action(Map.of("only", fn("only", 1)));
        when(this.functions.execute(any(), any(), any())).thenReturn(Mono.just(true));
        when(this.runs.start(any(), anyInt())).thenReturn(Mono.just(Optional.empty()));

        StepVerifier.create(this.listener.run(this.message)).expectNext(true).verifyComplete();
        verify(this.functions).execute(any(), any(), any());
    }
}
