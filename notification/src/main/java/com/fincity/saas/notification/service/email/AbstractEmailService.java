package com.fincity.saas.notification.service.email;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.notification.enums.ChannelType;
import com.fincity.saas.notification.enums.NotificationChannelType;
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
		return msgService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
				NotificationMessageResourceService.MAIL_SEND_ERROR, params);
	}

	protected Mono<Boolean> hasValidConnection(Map<String, Object> connection) {

		if (connection == null || connection.isEmpty())
			return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
					NotificationMessageResourceService.MAIL_SEND_ERROR, "Connection details are missing");

		NotificationChannelType connectionChannelType = NotificationChannelType
				.getFromConnectionSubType(String.valueOf(connection.get("connectionSubType")));

		if (connectionChannelType == null || !connectionChannelType.equals(this.getChannelType()))
			return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
					NotificationMessageResourceService.MAIL_SEND_ERROR, "Connection details are missing");

		return Mono.just(Boolean.TRUE);
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Object> getConnectionDetails(Map<String, Object> connection) {

		if (connection == null || !connection.containsKey("connectionDetails"))
			return Map.of();

		return (Map<String, Object>) connection.getOrDefault("connectionDetails", Map.of());
	}

	protected String generateContentId(String fromAddress) {

		if (fromAddress == null)
			return null;

		String domain = fromAddress.contains("@") ? fromAddress.split("@")[1] : "modlix.com";

		return System.currentTimeMillis() + "." + UniqueUtil.shortUUID() + "@" + domain;
	}
}
