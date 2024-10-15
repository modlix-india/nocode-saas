package com.fincity.saas.commons.mq.events;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.data.CircularLinkedList;
import com.fincity.saas.commons.util.data.DoublePointerNode;

import jakarta.annotation.PostConstruct;
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

	public Mono<Boolean> createEvent(EventQueObject queObj) {

		this.nextRoutingKey = nextRoutingKey.getNext();
		return Mono.just(queObj)
				.flatMap(q -> Mono.deferContextual(cv -> {
					if (!cv.hasKey(LogUtil.DEBUG_KEY))
						return Mono.just(q);
					q.setXDebug(cv.get(LogUtil.DEBUG_KEY)
							.toString());
					return Mono.just(q);
				}))
				.flatMap(q -> Mono.fromCallable(() -> {
					amqpTemplate.convertAndSend(exchange, nextRoutingKey.getItem(), q);
					return true;
				}));
	}
}
