package com.fincity.saas.notification.enums;

import org.jooq.EnumType;

public enum NotificationDeliveryStatus implements EnumType {

	NO_INFO("NO_INFO"),
	FAILED("FAILED"),
	PENDING("PENDING"),
	CANCELLED("CANCELLED"),
	QUEUED("QUEUED"),
	SENT("SENT"),
	DELIVERED("DELIVERED"),
	READ("READ"),
	ERROR("ERROR");

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
