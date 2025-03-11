package com.fincity.saas.notification.mq.action.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.saas.notification.document.Connection;
import com.fincity.saas.notification.enums.ChannelType;
import com.fincity.saas.notification.model.SendRequest;
import com.fincity.saas.notification.service.NotificationConnectionService;

import reactor.core.publisher.Mono;

@Service
public abstract class AbstractMessageService implements ChannelType {

	private NotificationConnectionService connectionService;

	@Autowired
	private void setConnectionService(NotificationConnectionService connectionService) {
		this.connectionService = connectionService;
	}

	protected boolean isValid(SendRequest sendRequest) {
		return sendRequest != null && sendRequest.isValid(this.getChannelType());
	}

	protected Mono<Connection> getConnection(String appCode, String clientCode, String connectionName) {
		return this.connectionService.getNotificationConn(appCode, clientCode, connectionName);
	}

}
