package com.fincity.saas.notification.mq.action.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.notification.document.Connection;
import com.fincity.saas.notification.enums.ChannelType;
import com.fincity.saas.notification.enums.NotificationDeliveryStatus;
import com.fincity.saas.notification.exception.NotificationDeliveryException;
import com.fincity.saas.notification.model.SendRequest;
import com.fincity.saas.notification.service.NotificationConnectionService;
import com.fincity.saas.notification.service.SentNotificationService;

import reactor.core.publisher.Mono;

@Service
public abstract class AbstractMessageService implements IMessageService, ChannelType {

	private NotificationConnectionService connectionService;

	private SentNotificationService sentNotificationService;

	@Autowired
	private void setConnectionService(NotificationConnectionService connectionService) {
		this.connectionService = connectionService;
	}

	@Autowired
	private void setSentNotificationService(SentNotificationService sentNotificationService) {
		this.sentNotificationService = sentNotificationService;
	}

	private boolean isValid(SendRequest sendRequest) {
		return sendRequest != null && sendRequest.isValid(this.getChannelType());
	}

	private Mono<Connection> getConnection(SendRequest request) {
		return this.connectionService.getNotificationConn(request.getAppCode(), request.getClientCode(),
				request.getConnections().get(this.getChannelType().getLiteral()));
	}

	private Mono<Boolean> notificationSent(SendRequest sendRequest) {
		return this.sendNotification(sendRequest, NotificationDeliveryStatus.SENT).map(request -> Boolean.TRUE);
	}

	private Mono<Boolean> notificationFailed(SendRequest sendRequest) {
		return this.sendNotification(sendRequest, NotificationDeliveryStatus.FAILED).map(request -> Boolean.FALSE);
	}

	private Mono<SendRequest> sendNotification(SendRequest sendRequest, NotificationDeliveryStatus deliveryStatus) {
		return sentNotificationService.toNetworkNotification(sendRequest, deliveryStatus);
	}

	@Override
	public Mono<Boolean> execute(SendRequest request) {

		if (!isValid(request))
			return Mono.just(Boolean.FALSE);

		return FlatMapUtil.flatMapMono(

				() -> this.getConnection(request),

				connection -> this.execute(request.getChannels().get(this.getChannelType()), connection),

				(connection, executed) -> Boolean.TRUE.equals(executed) ? this.notificationSent(request) : Mono.just(Boolean.FALSE)
		).onErrorResume(NotificationDeliveryException.class, ex ->
				this.notificationFailed(request.setChannelErrorInfo(ex, this.getChannelType())));
	}

}
