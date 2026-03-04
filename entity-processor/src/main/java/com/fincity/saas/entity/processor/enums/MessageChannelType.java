package com.fincity.saas.entity.processor.enums;

import org.jooq.EnumType;

import lombok.Getter;

@Getter
public enum MessageChannelType implements EnumType {

	DISABLED("DISABLED"),
	CALL("CALL"),
	WHATS_APP("WHATS_APP"),
	WHATS_APP_TEMPLATE("WHATS_APP_TEMPLATE"),
	IN_APP("IN_APP"),
	SMS("TEXT");

	private final String literal;

	MessageChannelType(String literal) {
		this.literal = literal;
	}

	public static MessageChannelType lookupLiteral(String literal) {
		return EnumType.lookupLiteral(MessageChannelType.class, literal);
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
