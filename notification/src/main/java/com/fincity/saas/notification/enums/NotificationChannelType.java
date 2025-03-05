package com.fincity.saas.notification.enums;

import org.jooq.EnumType;

import com.fincity.saas.notification.service.NotificationMessageResourceService;

public enum NotificationChannelType implements EnumType {

	DISABLED("DISABLED"),
	EMAIL("EMAIL"),
	IN_APP("IN_APP"),
	MOBILE_PUSH("MOBILE_PUSH"),
	WEB_PUSH("WEB_PUSH"),
	SMS("SMS");

	private final String literal;

	NotificationChannelType(String literal) {
		this.literal = literal;
	}

	public static NotificationChannelType lookupLiteral(String literal) {
		return EnumType.lookupLiteral(NotificationChannelType.class, literal);
	}

	public static NotificationChannelType getFromConnectionSubType(String connectionSubType) {

		if (!connectionSubType.startsWith(NotificationMessageResourceService.NOTIFICATION_PREFIX))
			return null;

		connectionSubType = connectionSubType.substring(NotificationMessageResourceService.NOTIFICATION_PREFIX.length());

		return NotificationChannelType.valueOf(connectionSubType.split("_")[0].toUpperCase());
	}

	@Override
	public String getLiteral() {
		return literal;
	}

	@Override
	public String getName() {
		return null;
	}

	public String getQueueName(String exchangeName) {
		return exchangeName + "." + this.getLiteral().toLowerCase();
	}
}
