package com.fincity.saas.notification.exception;

import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.notification.enums.NotificationChannelType;

public class NotificationDeliveryException extends GenericException {

	public NotificationDeliveryException(NotificationChannelType channelType, String message) {
		super(HttpStatus.INTERNAL_SERVER_ERROR, channelType.getLiteral() + " " + message);
	}

	public NotificationDeliveryException(NotificationChannelType channelType, String message, Throwable ex) {
		super(HttpStatus.INTERNAL_SERVER_ERROR, channelType.getLiteral() + " " + message, ex);
	}

	public NotificationDeliveryException(NotificationChannelType channelType, Throwable ex) {
		super(HttpStatus.INTERNAL_SERVER_ERROR, channelType.getLiteral() + " " + ex.getMessage(), ex);
	}
}
