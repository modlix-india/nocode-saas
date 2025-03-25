package com.fincity.saas.notification.service.channel.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.notification.document.Connection;
import com.fincity.saas.notification.enums.ChannelType;
import com.fincity.saas.notification.enums.NotificationChannelType;
import com.fincity.saas.notification.exception.NotificationDeliveryException;
import com.fincity.saas.notification.service.NotificationMessageResourceService;

import reactor.core.publisher.Mono;

@Service
public abstract class AbstractEmailService implements ChannelType {

	protected NotificationMessageResourceService msgService;

	protected Logger logger;

	protected AbstractEmailService() {
		logger = LoggerFactory.getLogger(this.getClass());
	}

	@Autowired
	private void setMessageResourceService(NotificationMessageResourceService messageResourceService) {
		this.msgService = messageResourceService;
	}

	@Override
	public NotificationChannelType getChannelType() {
		return NotificationChannelType.EMAIL;
	}

	protected <T> Mono<T> throwMailSendError(Object... params) {
		return msgService.throwMessage(msg -> new NotificationDeliveryException(this.getChannelType(), msg),
				NotificationMessageResourceService.MAIL_SEND_ERROR, params);
	}

	protected Mono<Boolean> hasValidConnection(Connection connection) {

		if (connection == null)
			return this.throwMailSendError("Connection details are missing");

		NotificationChannelType connectionChannelType = NotificationChannelType
				.getFromConnectionSubType(connection.getConnectionSubType());

		if (connectionChannelType == null || !connectionChannelType.equals(this.getChannelType()))
			return this.throwMailSendError("Connection details are missing");

		return Mono.just(Boolean.TRUE);
	}

	protected String generateContentId(String fromAddress) {

		if (fromAddress == null)
			return null;

		String domain = fromAddress.contains("@") ? fromAddress.split("@")[1] : "modlix.com";

		return System.currentTimeMillis() + "." + UniqueUtil.shortUUID() + "@" + domain;
	}
}
