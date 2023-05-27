package com.fincity.saas.commons.mq.events;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mq.events.exception.EventCreationException;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.commons.util.data.CircularLinkedList;
import com.fincity.saas.commons.util.data.DoublePointerNode;

import reactor.core.publisher.Mono;

@Service
public class EventCreationService {

	@Value("${events.mq.exchange:events}")
	private String exchange;

	@Value("${events.mq.routingkeys:events1,events2,events3}")
	private String routingKey;

	@Autowired
	private AmqpTemplate amqpTemplate;

	private DoublePointerNode<String> nextRoutingKey;

	@PostConstruct
	protected void init() {

		nextRoutingKey = new CircularLinkedList<>(this.routingKey.split(",")).getHead();
	}

	public Mono<Boolean> createEvent(String appCode, String clientCode, String eventName, Object authentication,
	        Object data) {

		Map<String, Object> eventMessage = new HashMap<>();

		if (StringUtil.safeIsBlank(appCode))
			return Mono.error(new EventCreationException("appCode"));

		if (StringUtil.safeIsBlank(clientCode))
			return Mono.error(new EventCreationException("clientCode"));

		if (StringUtil.safeIsBlank(eventName))
			return Mono.error(new EventCreationException("eventName"));

		eventMessage.put("appCode", appCode);
		eventMessage.put("clientCode", clientCode);
		eventMessage.put("eventName", eventName);

		if (authentication != null)
			eventMessage.put("authentication", authentication);

		if (data != null)
			eventMessage.put("data", data);

		this.nextRoutingKey = nextRoutingKey.getNext();
		return Mono.fromCallable(() -> {
			amqpTemplate.convertAndSend(exchange, nextRoutingKey.getItem(), eventMessage);
			return true;
		});
	}

	public Mono<Boolean> createEvent(String appCode, String clientCode, String eventName, Object data) {
		return this.createEvent(appCode, clientCode, eventName, null, data);
	}
}
