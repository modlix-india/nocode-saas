package com.fincity.saas.notification.enums;

import org.jooq.EnumType;

import lombok.Getter;

@Getter
public enum NotificationStage implements EnumType {

	PLATFORM("Platform", 0),
	GATEWAY("Gateway", 1),
	NETWORK("Network", 2);

	private final String literal;
	private final Integer level;

	NotificationStage(String literal, Integer level) {
		this.literal = literal;
		this.level = level;
	}

	public static NotificationStage lookupLiteral(String literal) {
		return EnumType.lookupLiteral(NotificationStage.class, literal);
	}

	@Override
	public String getLiteral() {
		return literal;
	}

	@Override
	public String getName() {
		return this.name();
	}
}
