package com.fincity.saas.notification.mq;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.notification.enums.NotificationDeliveryStatus;
import com.fincity.saas.notification.model.request.SendRequest;
import com.fincity.saas.notification.service.SentNotificationService;

import reactor.core.publisher.Mono;

@Service
public class NotificationMessageProducer {

	@Value("${events.mq.exchange.fanout:notification.fanout.exchange}")
	private String fanoutExchangeName;

	private AmqpTemplate amqpTemplate;

	private SentNotificationService sentNotificationService;

	@Autowired
	public void setAmqpTemplate(AmqpTemplate amqpTemplate) {
		this.amqpTemplate = amqpTemplate;
	}

	@Autowired
	private void setSentNotificationService(SentNotificationService sentNotificationService) {
		this.sentNotificationService = sentNotificationService;
	}

	public Mono<SendRequest> broadcast(SendRequest sendRequest) {
		return FlatMapUtil.flatMapMono(
				() -> Mono.just(sendRequest),

				request -> Mono.deferContextual(cv -> {
					if (!cv.hasKey(LogUtil.DEBUG_KEY))
						return Mono.just(request);

					request.setXDebug(cv.get(LogUtil.DEBUG_KEY));
					return Mono.just(request);
				}),

				(request, dRequest) -> sentNotificationService.toGatewayNotification(dRequest,
						NotificationDeliveryStatus.QUEUED),

				(request, dRequest, sRequest) -> Mono.fromCallable(() -> {
					amqpTemplate.convertAndSend(fanoutExchangeName, "", dRequest);
					return dRequest;
				}));
	}
}
