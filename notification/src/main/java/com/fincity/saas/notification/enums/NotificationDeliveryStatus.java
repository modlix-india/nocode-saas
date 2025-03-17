package com.fincity.saas.notification.enums;

import org.jooq.EnumType;

public enum NotificationDeliveryStatus implements EnumType {

	NO_INFO("NO_INFO"),
	CREATED("CREATED"),
	ERROR("ERROR"),
	CANCELLED("CANCELLED"),
	QUEUED("QUEUED"),
	SENT("SENT"),
	DELIVERED("DELIVERED"),
	READ("READ"),
	FAILED("FAILED");

	private final String literal;

	NotificationDeliveryStatus(String literal) {
		this.literal = literal;
	}

	public static NotificationDeliveryStatus lookupLiteral(String literal) {
		return EnumType.lookupLiteral(NotificationDeliveryStatus.class, literal);
	}

	@Override
	public String getLiteral() {
		return literal;
	}

	@Override
	public String getName() {
		return null;
	}
}
