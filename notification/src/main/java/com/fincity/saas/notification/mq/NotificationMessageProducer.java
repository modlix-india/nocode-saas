package com.fincity.saas.notification.mq;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.notification.model.SendRequest;

import reactor.core.publisher.Mono;

@Service
public class NotificationMessageProducer {

	@Value("${events.mq.exchange.fanout:notification.fanout.exchange}")
	private String fanoutExchangeName;

	private AmqpTemplate amqpTemplate;

	@Autowired
	public void setAmqpTemplate(AmqpTemplate amqpTemplate) {
		this.amqpTemplate = amqpTemplate;
	}

	public Mono<Boolean> broadcast(SendRequest sendRequest) {

		return Mono.just(sendRequest)
				.flatMap(req -> Mono.deferContextual(cv -> {
					if (!cv.hasKey(LogUtil.DEBUG_KEY))
						return Mono.just(req);

					req.setXDebug(cv.get(LogUtil.DEBUG_KEY));
					return Mono.just(req);
				}))
				.flatMap(req -> Mono.fromCallable(() -> {
					amqpTemplate.convertAndSend(fanoutExchangeName, "", req);
					return true;
				}));
	}
}
