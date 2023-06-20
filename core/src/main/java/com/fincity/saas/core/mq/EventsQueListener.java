package com.fincity.saas.core.mq;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.core.enums.EventActionTaskType;
import com.fincity.saas.core.mq.action.services.EventCallFunctionService;
import com.fincity.saas.core.mq.action.services.EventEmailService;
import com.fincity.saas.core.mq.action.services.IEventActionService;
import com.fincity.saas.core.service.EventActionService;
import com.rabbitmq.client.Channel;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Component
public class EventsQueListener {

	private static final Logger logger = org.slf4j.LoggerFactory.getLogger(EventsQueListener.class);

	@Autowired
	private EventActionService eventActionService;

	@Autowired
	private EventCallFunctionService functionService;

	@Autowired
	private EventEmailService emailService;

	private Map<EventActionTaskType, IEventActionService> actionServices = new EnumMap<>(EventActionTaskType.class);

	@PostConstruct
	protected void init() {

		this.actionServices.put(EventActionTaskType.SEND_EMAIL, emailService);
		this.actionServices.put(EventActionTaskType.CALL_COREFUNCTION, functionService);
	}

	@RabbitListener(queues = "#{'${events.mq.queues:events1,events2,events3}'.split(',')}", containerFactory = "directMesageListener", messageConverter = "jsonMessageConverter")
	public Mono<Void> receive(@Payload EventQueObject message, Channel channel,
	        @Header(AmqpHeaders.DELIVERY_TAG) long tag) {

		Mono<Boolean> receivedMono = FlatMapUtil.flatMapMono(

		        () -> eventActionService.read(message.getEventName(), message.getAppCode(), message.getClientCode()),

		        eventAction ->
				{

			        if (eventAction.getTasks() == null || eventAction.getTasks()
			                .isEmpty())
				        return Mono.just(true);

			        return Flux.fromIterable(eventAction.getTasks()
			                .values())
			                .filter(task -> this.actionServices.containsKey(task.getType()))
			                .flatMap(task -> this.actionServices.get(task.getType())
			                        .execute(eventAction, task, message))
			                .onErrorResume(t ->
							{
				                logger.error("Error while executing tasks on : {} ", message, t);
				                return Mono.just(Boolean.FALSE);
			                })
			                .reduce((a, b) -> a.booleanValue() && b.booleanValue());
		        }

		);

		if (message.getXDebug() != null) {
			receivedMono = receivedMono.contextWrite(Context.of(LogUtil.DEBUG_KEY, message.getXDebug()));
		}
		return receivedMono.contextWrite(Context.of(LogUtil.METHOD_NAME, "EventsQueListener.receive"))
		        .then();

	}

}