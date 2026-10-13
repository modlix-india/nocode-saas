package com.fincity.saas.core.mq;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.document.EventAction;
import com.fincity.saas.commons.core.enums.EventActionTaskType;
import com.fincity.saas.commons.core.jooq.enums.CoreEventRunsStatus;
import com.fincity.saas.commons.core.model.EventActionTask;
import com.fincity.saas.commons.core.model.EventRunTask;
import com.fincity.saas.commons.core.mq.services.EventCallFunctionService;
import com.fincity.saas.commons.core.mq.services.EventEmailService;
import com.fincity.saas.commons.core.mq.services.IEventActionService;
import com.fincity.saas.commons.core.scheduler.EventScheduler;
import com.fincity.saas.commons.core.scheduler.ScheduledEventRunner;
import com.fincity.saas.commons.core.service.EventActionService;
import com.fincity.saas.commons.core.service.EventRunService;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.security.dto.Client;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.util.LogUtil;
import com.rabbitmq.client.Channel;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Component
public class EventsQueListener implements ScheduledEventRunner {

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(EventsQueListener.class);

    static final String ANONYMOUS_AUTHORITY = "Authorities._Anonymous";

    /**
     * Held only by the anonymous context built here, i.e. by an event that was raised without an
     * authentication: a scheduler tick or one-off, or a message-service hand-off (WHATSAPP_INBOUND,
     * CALL_EVENT). A browser's anonymous context (security's AuthenticationService) carries only
     * {@link #ANONYMOUS_AUTHORITY}, and an event raised by CreateEvent runs as its raiser. So a server
     * function meant to be run only by an event sets {@code executeAuth = "Authorities._Event_Runner"}:
     * the event runs it, a direct POST /api/core/function/execute/... gets 403.
     */
    static final String EVENT_RUNNER_AUTHORITY = "Authorities._Event_Runner";

    private final EventActionService eventActionService;
    private final EventCallFunctionService functionService;
    private final EventEmailService emailService;

    private final IFeignSecurityService securityService;

    private final EventScheduler eventScheduler;

    private final EventRunService eventRunService;

    private final UnhandledEventLog unhandledEventLog = new UnhandledEventLog();

    private final Map<EventActionTaskType, IEventActionService> actionServices = new EnumMap<>(
            EventActionTaskType.class);

    public EventsQueListener(EventActionService eventActionService, EventCallFunctionService functionService,
                             EventEmailService emailService, IFeignSecurityService securityService,
                             EventScheduler eventScheduler, EventRunService eventRunService) {
        this.eventActionService = eventActionService;
        this.functionService = functionService;
        this.emailService = emailService;
        this.securityService = securityService;
        this.eventScheduler = eventScheduler;
        this.eventRunService = eventRunService;
    }

    @PostConstruct
    protected void init() {
        this.actionServices.put(EventActionTaskType.SEND_EMAIL, emailService);
        this.actionServices.put(EventActionTaskType.CALL_CORE_FUNCTION, functionService);
    }

    @RabbitListener(queues = "#{'${events.mq.queues:events1,events2,events3}'.split(',')}", containerFactory = "directMessageListener", messageConverter = "jsonMessageConverter")
    public Mono<Void> receive(@Payload EventQueObject qob, Channel channel,
                              @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        return this.run(qob).then();
    }

    /**
     * Runs one event's tasks under the context it was raised with (its own
     * authentication, or the client's anonymous event-runner context) and answers
     * their outcome. The queue listener and the scheduler both come through here:
     * the scheduler runs the ticks it claims in this JVM rather than publishing
     * them, so the instance that claimed a tick is the one that does its work.
     */
    @Override
    public Mono<Boolean> run(EventQueObject qob) {

        // Whether this run has put its row in the event run log. Anything that stops it before
        // that point (the client cannot be found, the action cannot be read) is recorded at the
        // end instead, so the log never silently misses a run.
        AtomicBoolean logged = new AtomicBoolean(false);

        Mono<Boolean> receivedMono = FlatMapUtil.flatMapMono(

                () -> Mono.just(qob),

                message -> eventActionService.read(message.getEventName(), message.getAppCode(),
                        message.getClientCode())
                        // Still dropped, as before, but no longer silently: see UnhandledEventLog,
                        // and the NO_ACTION row in the run log.
                        .switchIfEmpty(Mono.defer(() -> {
                            logged.set(true);
                            String warning = this.unhandledEventLog.warning(message, System.currentTimeMillis());
                            if (warning != null)
                                logger.warn(warning);
                            Mono<ObjectWithUniqueID<EventAction>> none = Mono.empty();
                            return this.eventRunService == null ? none
                                    : this.eventRunService.recordNoAction(message).then(none);
                        })),

                (message, eventActionObject) -> this.runLogged(eventActionObject.getObject(), message, logged)

        );

        // A run the scheduler raised is closed with the outcome of these tasks:
        // DONE or FAILED for a cron tick, DONE or retry-with-backoff for a one-off.
        // Every other event passes through unchanged.
        receivedMono = this.eventScheduler.trackCompletion(qob, receivedMono);

        if (qob.getXDebug() != null) {
            receivedMono = receivedMono.contextWrite(Context.of(LogUtil.DEBUG_KEY, qob.getXDebug()));
        }

        // Restore the draft marker alongside the debug key. An event raised by a
        // draft-surface write must keep executing on the draft surface: without
        // this, a CALL_CORE_FUNCTION task writing through CoreServices.Storage
        // resolved isDraft() as false and landed in the LIVE database.
        if (qob.isDraft()) {
            receivedMono = receivedMono.contextWrite(Context.of(LogUtil.DRAFT_KEY, Boolean.TRUE));
        }

        Mono<Boolean> withContext;
        if (qob.getAuthentication() != null) {
            withContext = receivedMono.contextWrite(ReactiveSecurityContextHolder
                    .withSecurityContext(Mono.just(new SecurityContextImpl(qob.getAuthentication()))));
        } else {
            final Mono<Boolean> finalReceivedMono = receivedMono;
            withContext = this.securityService.getClientByCode(qob.getClientCode())
                    .map(client -> makeAnonymousContextAuth
                            (qob.getAppCode(), qob.getClientCode(), client))
                    .flatMap(ca -> finalReceivedMono.contextWrite(ReactiveSecurityContextHolder
                            .withSecurityContext(Mono.just(new SecurityContextImpl(ca)))));
        }

        return this.logUnstarted(qob, withContext, logged)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "EventsQueListener.run"));
    }

    /**
     * Records a run that ended before it reached its action, which nothing else would: the
     * client could not be found (an empty answer, and the event used to vanish), or reading the
     * action failed. The error still propagates after it is recorded.
     */
    private Mono<Boolean> logUnstarted(EventQueObject qob, Mono<Boolean> run, AtomicBoolean logged) {

        if (this.eventRunService == null)
            return run;

        return run
                .switchIfEmpty(Mono.defer(() -> logged.get() ? Mono.<Boolean>empty()
                        : this.eventRunService.record(qob, CoreEventRunsStatus.FAILED, "The client '"
                                + qob.getClientCode() + "' could not be found, so the event could not run.")
                                .then(Mono.<Boolean>empty())))
                .onErrorResume(e -> logged.get() ? Mono.error(e)
                        : this.eventRunService.record(qob, CoreEventRunsStatus.FAILED,
                                "The event could not run: " + EventRunService.errorOf(e)).then(Mono.error(e)));
    }

    /**
     * Runs the action's tasks with a row in the event run log: RUNNING before the first task,
     * then SUCCESS or FAILED with each task's own outcome.
     */
    private Mono<Boolean> runLogged(EventAction eventAction, EventQueObject message, AtomicBoolean logged) {

        logged.set(true);

        if (this.eventRunService == null)
            return this.executeTasks(eventAction, message);

        long started = System.currentTimeMillis();
        List<EventRunTask> outcomes = new ArrayList<>();
        int taskCount = eventAction.getTasks() == null ? 0 : eventAction.getTasks().size();

        return this.eventRunService.start(message, taskCount)
                .flatMap(runId -> this.executeTasks(eventAction, message, outcomes)
                        .flatMap(ok -> this.eventRunService.finish(runId, started, ok, outcomes, null).thenReturn(ok))
                        .onErrorResume(e -> this.eventRunService
                                .finish(runId, started, false, outcomes, EventRunService.errorOf(e))
                                .then(Mono.error(e))));
    }

    // The tasks run one after another in their order, so a task can rely on the ones before it
    // having finished. They used to start all at once, and the first error cancelled the tasks
    // still running and dropped the ones not yet started. Now each task is on its own: one that
    // fails is logged and counts as false, and the rest still run. The result is the AND of all
    // the tasks, which is what EventScheduler.trackCompletion marks a scheduled run DONE or
    // FAILED by. Package-private so a unit test can drive it without the queue.
    Mono<Boolean> executeTasks(EventAction eventAction, EventQueObject message) {
        return this.executeTasks(eventAction, message, null);
    }

    /** As above, adding each task's outcome to {@code outcomes}, in the order they ran, when it is not null. */
    Mono<Boolean> executeTasks(EventAction eventAction, EventQueObject message, List<EventRunTask> outcomes) {

        if (eventAction.getTasks() == null || eventAction.getTasks()
                .isEmpty())
            return Mono.just(true);

        // Sorted on the map entry and not with EventActionTask.compareTo: a stored task's own key
        // can be null, its identity is the map key. Ties in order go by that key.
        List<Map.Entry<String, EventActionTask>> tasks = eventAction.getTasks()
                .entrySet()
                .stream()
                .sorted(Comparator
                        .comparingInt((Map.Entry<String, EventActionTask> e) -> e.getValue() == null
                                || e.getValue().getOrder() == null ? 0 : e.getValue().getOrder())
                        .thenComparing(Map.Entry::getKey, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();

        return Flux.fromIterable(tasks)
                .concatMap(entry -> this.executeTask(eventAction, entry.getKey(), entry.getValue(), message,
                        outcomes))
                .reduce(Boolean.TRUE, Boolean::logicalAnd);
    }

    private Mono<Boolean> executeTask(EventAction eventAction, String taskKey, EventActionTask task,
                                      EventQueObject message, List<EventRunTask> outcomes) {

        long started = System.currentTimeMillis();
        EventRunTask outcome = new EventRunTask()
                .setKey(taskKey)
                .setType(task == null || task.getType() == null ? null : task.getType().name())
                .setTarget(targetOf(task))
                .setOrder(task == null ? null : task.getOrder())
                .setStartedAt(started);

        // Deferred so an executor that throws instead of returning an error is caught here too.
        Mono<Boolean> result = Mono.defer(() -> {

                    IEventActionService service = task == null ? null : this.actionServices.get(task.getType());

                    logger.debug("Executing task : Present - {} : {} ", service != null, task);

                    if (service == null)
                        return Mono.<Boolean>error(new IllegalArgumentException(
                                "Invalid task type : " + (task == null ? null : task.getType())));

                    return service.execute(eventAction, task, message);
                })
                // A task that finished without a result has nothing to report against it.
                .defaultIfEmpty(Boolean.TRUE)
                .onErrorResume(t -> {
                    logger.error("Error while executing task {} of event {} for app {} and client {} : {} ", taskKey,
                            message.getEventName(), message.getAppCode(), message.getClientCode(), message, t);
                    outcome.setError(EventRunService.errorOf(t))
                            .setDetail(EventRunService.detailOf(t));
                    return Mono.just(Boolean.FALSE);
                });

        if (outcomes == null)
            return result;

        return result.doOnNext(ok -> {
            outcome.setStatus(Boolean.TRUE.equals(ok) ? EventRunTask.Status.SUCCESS : EventRunTask.Status.FAILED)
                    .setDurationMs(System.currentTimeMillis() - started);
            if (!Boolean.TRUE.equals(ok) && outcome.getError() == null)
                outcome.setError("The task reported that it did not succeed.");
            outcomes.add(outcome);
        });
    }

    /** The function a task calls, namespace.name, when its parameters name one. */
    static String targetOf(EventActionTask task) {

        if (task == null || task.getParameters() == null)
            return null;

        Object namespace = task.getParameters().get("namespace");
        Object name = task.getParameters().get("name");
        if (name == null || name.toString().isBlank())
            return null;

        return namespace == null || namespace.toString().isBlank() ? name.toString() : namespace + "." + name;
    }

    // Package-private and static so a unit test can pin the constructor argument order.
    // ContextAuthentication's all-args order is ..., accessTokenExpiryAt, urlClientCode,
    // urlAppCode, verifiedAppCode. Passing appCode before clientCode (the old order) put the
    // app code in urlClientCode, so a scheduled run's Storage call went to a client named
    // after the app and ScheduleEvent / CancelScheduledEvent read the event under the wrong app.
    static ContextAuthentication makeAnonymousContextAuth(String appCode, String clientCode, Client client) {

        return new ContextAuthentication(
                new ContextUser()
                        .setId(BigInteger.ZERO)
                        .setCreatedBy(BigInteger.ZERO)
                        .setUpdatedBy(BigInteger.ZERO)
                        .setCreatedAt(LocalDateTime.now())
                        .setUpdatedAt(LocalDateTime.now())
                        .setClientId(client.getId())
                        .setUserName("_Anonymous")
                        .setEmailId("nothing@nothing")
                        .setPhoneNumber("+910000000000")
                        .setFirstName("Anonymous")
                        .setLastName("")
                        .setLocaleCode("en")
                        .setPassword("")
                        .setPasswordHashed(false)
                        .setAccountNonExpired(true)
                        .setAccountNonLocked(true)
                        .setCredentialsNonExpired(true)
                        .setNoFailedAttempt((short) 0)
                        .setStringAuthorities(List.of(ANONYMOUS_AUTHORITY, EVENT_RUNNER_AUTHORITY)),
                false,
                client.getId(),
                client.getCode(),
                client.getTypeCode(),
                client.getLevelType(),
                client.getCode(),
                "",
                LocalDateTime.MAX,
                clientCode,
                appCode,
                null);
    }

}
