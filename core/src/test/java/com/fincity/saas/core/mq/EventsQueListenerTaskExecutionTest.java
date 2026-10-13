package com.fincity.saas.core.mq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import com.fincity.saas.commons.core.document.EventAction;
import com.fincity.saas.commons.core.enums.EventActionTaskType;
import com.fincity.saas.commons.core.model.EventActionTask;
import com.fincity.saas.commons.core.mq.services.EventCallFunctionService;
import com.fincity.saas.commons.core.mq.services.EventEmailService;
import com.fincity.saas.commons.mq.events.EventQueObject;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * An EventAction's tasks run one after another in their order, so one can rely on another having
 * finished, and each is on its own: a task that fails counts as false but does not stop the rest.
 * The action reports the AND of its tasks, which the scheduler uses to mark a run DONE or FAILED.
 */
class EventsQueListenerTaskExecutionTest {

    private static final String RESULT = "result";

    private final List<String> log = new CopyOnWriteArrayList<>();

    private EventsQueListener listener;

    private final EventQueObject message = new EventQueObject().setEventName("leadCreated")
            .setAppCode("leadzumptwo")
            .setClientCode("FIN");

    @BeforeEach
    void setUp() {

        EventCallFunctionService functionService = mock(EventCallFunctionService.class);
        EventEmailService emailService = mock(EventEmailService.class);

        when(functionService.execute(any(), any(), any())).thenAnswer(this::run);
        when(emailService.execute(any(), any(), any())).thenAnswer(this::run);

        listener = new EventsQueListener(null, functionService, emailService, null, null, null);
        listener.init();
    }

    // Logs when the executor is CALLED and when its Mono completes. A slow task is delayed so a
    // second task started alongside it would log its start before the first one's end.
    private Mono<Boolean> run(InvocationOnMock invocation) {

        EventActionTask task = invocation.getArgument(1);
        String name = (String) task.getParameters().get("name");
        Object result = task.getParameters().get(RESULT);

        log.add("start " + name);

        if ("throw".equals(result))
            throw new IllegalStateException("thrown by " + name);

        Mono<Boolean> mono = switch (String.valueOf(result)) {
            case "error" -> Mono.error(new IllegalStateException("failed " + name));
            case "empty" -> Mono.empty();
            case "false" -> Mono.just(false);
            default -> Mono.just(true);
        };

        return Mono.delay(Duration.ofMillis(50))
                .then(mono)
                // doOnTerminate, not doFinally: doFinally runs after the completion has already
                // reached concatMap, which by then has started the next task.
                .doOnTerminate(() -> log.add("end " + name));
    }

    private static EventActionTask task(String key, Integer order, EventActionTaskType type, String name,
                                        String result) {
        return new EventActionTask().setKey(key)
                .setOrder(order)
                .setType(type)
                .setParameters(Map.of("name", name, RESULT, result));
    }

    private static EventAction action(Map<String, EventActionTask> tasks) {
        return new EventAction().setTasks(tasks);
    }

    @Test
    void tasksRunOneAfterAnotherInOrderNotInsertionOrder() {

        Map<String, EventActionTask> tasks = new LinkedHashMap<>();
        tasks.put("c", task("c", 3, EventActionTaskType.CALL_CORE_FUNCTION, "third", "true"));
        tasks.put("a", task("a", 1, EventActionTaskType.SEND_EMAIL, "first", "true"));
        tasks.put("b", task("b", 2, EventActionTaskType.CALL_CORE_FUNCTION, "second", "true"));

        StepVerifier.create(listener.executeTasks(action(tasks), message))
                .expectNext(true)
                .verifyComplete();

        assertEquals(List.of("start first", "end first", "start second", "end second", "start third",
                "end third"), log);
    }

    @Test
    void equalOrdersAreBrokenByTheMapKeyAndANullOrderIsZero() {

        Map<String, EventActionTask> tasks = new LinkedHashMap<>();
        tasks.put("z", task("z", 0, EventActionTaskType.CALL_CORE_FUNCTION, "z", "true"));
        tasks.put("late", task("late", 5, EventActionTaskType.CALL_CORE_FUNCTION, "late", "true"));
        tasks.put("m", task("m", null, EventActionTaskType.CALL_CORE_FUNCTION, "m", "true"));
        tasks.put("a", task("a", 0, EventActionTaskType.CALL_CORE_FUNCTION, "a", "true"));

        StepVerifier.create(listener.executeTasks(action(tasks), message))
                .expectNext(true)
                .verifyComplete();

        assertEquals(List.of("start a", "end a", "start m", "end m", "start z", "end z", "start late",
                "end late"), log);
    }

    @Test
    void aFailingMiddleTaskDoesNotStopTheLaterOnesAndTheActionFails() {

        Map<String, EventActionTask> tasks = new LinkedHashMap<>();
        tasks.put("a", task("a", 1, EventActionTaskType.CALL_CORE_FUNCTION, "first", "true"));
        tasks.put("b", task("b", 2, EventActionTaskType.CALL_CORE_FUNCTION, "second", "error"));
        tasks.put("c", task("c", 3, EventActionTaskType.SEND_EMAIL, "third", "true"));

        StepVerifier.create(listener.executeTasks(action(tasks), message))
                .expectNext(false)
                .verifyComplete();

        assertEquals(List.of("start first", "end first", "start second", "end second", "start third",
                "end third"), log);
    }

    @Test
    void anExecutorThatThrowsIsIsolatedTheSameWay() {

        Map<String, EventActionTask> tasks = new LinkedHashMap<>();
        tasks.put("a", task("a", 1, EventActionTaskType.CALL_CORE_FUNCTION, "first", "throw"));
        tasks.put("b", task("b", 2, EventActionTaskType.CALL_CORE_FUNCTION, "second", "true"));

        StepVerifier.create(listener.executeTasks(action(tasks), message))
                .expectNext(false)
                .verifyComplete();

        assertEquals(List.of("start first", "start second", "end second"), log);
    }

    @Test
    void anUnknownTaskTypeIsIsolatedTheSameWay() {

        Map<String, EventActionTask> tasks = new LinkedHashMap<>();
        tasks.put("a", task("a", 1, EventActionTaskType.CALL_CORE_FUNCTION, "first", "true"));
        tasks.put("b", task("b", 2, null, "unknown", "true"));
        tasks.put("c", task("c", 3, EventActionTaskType.SEND_EMAIL, "third", "true"));

        StepVerifier.create(listener.executeTasks(action(tasks), message))
                .expectNext(false)
                .verifyComplete();

        assertEquals(List.of("start first", "end first", "start third", "end third"), log);
    }

    @Test
    void aTaskReportingFalseFailsTheAction() {

        Map<String, EventActionTask> tasks = new LinkedHashMap<>();
        tasks.put("a", task("a", 1, EventActionTaskType.CALL_CORE_FUNCTION, "first", "false"));
        tasks.put("b", task("b", 2, EventActionTaskType.CALL_CORE_FUNCTION, "second", "true"));

        StepVerifier.create(listener.executeTasks(action(tasks), message))
                .expectNext(false)
                .verifyComplete();

        assertEquals(4, log.size());
    }

    @Test
    void tasksWithANullKeyDoNotThrow() {

        // A stored task's own key can be null; its identity is the map key. Same order on purpose,
        // so the tie has to be broken without EventActionTask.compareTo.
        Map<String, EventActionTask> tasks = new LinkedHashMap<>();
        tasks.put("b", task(null, 1, EventActionTaskType.CALL_CORE_FUNCTION, "b", "true"));
        tasks.put("a", task(null, 1, EventActionTaskType.CALL_CORE_FUNCTION, "a", "true"));

        StepVerifier.create(listener.executeTasks(action(tasks), message))
                .expectNext(true)
                .verifyComplete();

        assertEquals(List.of("start a", "end a", "start b", "end b"), log);
    }

    @Test
    void allSucceedingIsTrueAndAnEmptyTaskCountsAsSuccess() {

        Map<String, EventActionTask> tasks = new LinkedHashMap<>();
        tasks.put("a", task("a", 1, EventActionTaskType.CALL_CORE_FUNCTION, "first", "empty"));
        tasks.put("b", task("b", 2, EventActionTaskType.SEND_EMAIL, "second", "true"));

        StepVerifier.create(listener.executeTasks(action(tasks), message))
                .expectNext(true)
                .verifyComplete();

        assertEquals(4, log.size());
    }

    @Test
    void onlyEmptyTasksStillGiveAResult() {

        Map<String, EventActionTask> tasks = new LinkedHashMap<>();
        tasks.put("a", task("a", 1, EventActionTaskType.CALL_CORE_FUNCTION, "first", "empty"));

        StepVerifier.create(listener.executeTasks(action(tasks), message))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void noTasksIsTrue() {

        StepVerifier.create(listener.executeTasks(action(null), message))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(listener.executeTasks(action(Map.of()), message))
                .expectNext(true)
                .verifyComplete();

        assertEquals(List.of(), log);
    }
}
