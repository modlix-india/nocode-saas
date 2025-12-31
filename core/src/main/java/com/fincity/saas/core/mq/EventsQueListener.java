package com.fincity.saas.core.mq;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.enums.EventActionTaskType;
import com.fincity.saas.commons.core.mq.services.EventCallFunctionService;
import com.fincity.saas.commons.core.mq.services.EventEmailService;
import com.fincity.saas.commons.core.mq.services.IEventActionService;
import com.fincity.saas.commons.core.service.EventActionService;
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
public class EventsQueListener {

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(EventsQueListener.class);

    private final EventActionService eventActionService;
    private final EventCallFunctionService functionService;
    private final EventEmailService emailService;

    private final IFeignSecurityService securityService;

    private final Map<EventActionTaskType, IEventActionService> actionServices = new EnumMap<>(
            EventActionTaskType.class);

    public EventsQueListener(EventActionService eventActionService, EventCallFunctionService functionService,
                             EventEmailService emailService, IFeignSecurityService securityService) {
        this.eventActionService = eventActionService;
        this.functionService = functionService;
        this.emailService = emailService;
        this.securityService = securityService;
    }

    @PostConstruct
    protected void init() {
        this.actionServices.put(EventActionTaskType.SEND_EMAIL, emailService);
        this.actionServices.put(EventActionTaskType.CALL_CORE_FUNCTION, functionService);
    }

    @RabbitListener(queues = "#{'${events.mq.queues:events1,events2,events3}'.split(',')}", containerFactory = "directMessageListener", messageConverter = "jsonMessageConverter")
    public Mono<Void> receive(@Payload EventQueObject qob, Channel channel,
                              @Header(AmqpHeaders.DELIVERY_TAG) long tag) {

        Mono<Boolean> receivedMono = FlatMapUtil.flatMapMono(

                () -> Mono.just(qob),

                message -> eventActionService.read(message.getEventName(), message.getAppCode(),
                        message.getClientCode()),

                (message, eventActionObject) -> {
                    var eventAction = eventActionObject.getObject();

                    if (eventAction.getTasks() == null || eventAction.getTasks()
                            .isEmpty())
                        return Mono.just(true);

                    return Flux.fromIterable(eventAction.getTasks()
                                    .values())
                            .flatMap(task -> {

                                logger.debug("Executing task : Present - {} : {} ",
                                        this.actionServices.containsKey(task.getType()),
                                        task);

                                if (!this.actionServices.containsKey(task.getType()))
                                    return Mono.error(
                                            new IllegalArgumentException("Invalid task type : " + task.getType()));

                                return this.actionServices.get(task.getType())
                                        .execute(eventAction, task, message);
                            })
                            .onErrorResume(t -> {
                                logger.error("Error while executing tasks on : {} ", message, t);
                                return Mono.just(Boolean.FALSE);
                            })
                            .reduce(Boolean::logicalAnd);
                }

        );

        if (qob.getXDebug() != null) {
            receivedMono = receivedMono.contextWrite(Context.of(LogUtil.DEBUG_KEY, qob.getXDebug()));
        }

        if (qob.getAuthentication() != null) {
            return receivedMono.contextWrite(ReactiveSecurityContextHolder
                            .withSecurityContext(Mono.just(new SecurityContextImpl(qob.getAuthentication()))))
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "EventsQueListener.receive")).then();
        } else {
            final Mono<Boolean> finalReceivedMono = receivedMono;
            return this.securityService.getClientByCode(qob.getClientCode())
                    .map(client -> this.makeAnonymousContextAuth(qob.getAppCode(), qob.getClientCode(), client))
                    .flatMap(ca -> finalReceivedMono.contextWrite(ReactiveSecurityContextHolder
                            .withSecurityContext(Mono.just(new SecurityContextImpl(ca)))))
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "EventsQueListener.receive")).then();
        }
    }

    private ContextAuthentication makeAnonymousContextAuth(String appCode, String clientCode, Client client) {

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
                        .setStringAuthorities(List.of("Authorities._Anonymous")),
                false,
                client.getId(),
                client.getCode(),
                client.getTypeCode(),
                client.getLevelType(),
                client.getCode(),
                "",
                LocalDateTime.MAX,
                appCode,
                clientCode,
                null);
    }

}
