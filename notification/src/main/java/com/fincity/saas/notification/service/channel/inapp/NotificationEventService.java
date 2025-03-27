package com.fincity.saas.notification.service.channel.inapp;

import java.math.BigInteger;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;

import com.fincity.saas.notification.document.Connection;
import com.fincity.saas.notification.model.SendRequest;
import com.fincity.saas.notification.model.response.SendResponse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;
import reactor.util.concurrent.Queues;

@Service(value = "notificationEvent")
public class NotificationEventService extends AbstractInAppService implements IInAppService {

	private final Many<SendResponse> sink;

	public NotificationEventService() {
		this.sink = Sinks.many().multicast().onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE);
	}

	@Override
	public Mono<Boolean> sendMessage(SendRequest inAppMessage, Connection connection) {
		return null;
	}

	public Flux<ServerSentEvent<SendResponse>> subscribeToEvent(String clientCode, String appCode, BigInteger userId) {
		return this.sink.asFlux()
				.map(message -> ServerSentEvent.<SendResponse>builder()
						.data(message)
						.event("notification")
						.build());
	}

	private Mono<Boolean> isMessageForUser(SendRequest message, String appCode, String clientCode, BigInteger userId) {
		return Mono.just(message.getClientCode().equals(clientCode)
				&& message.getAppCode().equals(appCode)
				&& message.getUserId().equals(userId));
	}
}
