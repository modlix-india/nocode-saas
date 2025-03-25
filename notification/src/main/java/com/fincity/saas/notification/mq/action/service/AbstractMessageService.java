package com.fincity.saas.notification.mq.action.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.notification.document.Connection;
import com.fincity.saas.notification.enums.ChannelType;
import com.fincity.saas.notification.enums.NotificationDeliveryStatus;
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

	protected boolean isValid(SendRequest sendRequest) {
		return sendRequest != null && sendRequest.isValid(this.getChannelType());
	}

	protected Mono<Connection> getConnection(SendRequest request) {
		return this.connectionService.getNotificationConn(request.getAppCode(), request.getClientCode(),
				request.getConnections().get(this.getChannelType().getLiteral()));
	}

	protected Mono<Boolean> notificationSent(SendRequest sendRequest) {
		return this.sendNotification(sendRequest, NotificationDeliveryStatus.SENT);
	}

	protected Mono<Boolean> notificationFailed(SendRequest sendRequest) {
		return this.sendNotification(sendRequest, NotificationDeliveryStatus.FAILED);
	}

	private Mono<Boolean> sendNotification(SendRequest sendRequest, NotificationDeliveryStatus deliveryStatus) {
		return sentNotificationService.toNetworkNotification(sendRequest, deliveryStatus).map(request -> Boolean.TRUE);
	}

	@Override
	public Mono<Boolean> execute(SendRequest request) {

		if (!isValid(request))
			return Mono.just(Boolean.FALSE);

		return FlatMapUtil.flatMapMono(

				() -> getConnection(request),

				connection -> this.execute(request.getChannels()., connection),

				(connection, emailSent) -> notificationSent(request)
		);
	}



}
