package com.fincity.saas.message.enums.message.provider.whatsapp.business;

import org.jooq.EnumType;

import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;

@Getter
public enum TemplateStatus implements EnumType {

	APPROVED("APPROVED", "APPROVED"),
	IN_APPEAL("IN_APPEAL", "IN_APPEAL"),
	PENDING("PENDING", "PENDING"),
	REJECTED("REJECTED", "REJECTED"),
	PENDING_DELETION("PENDING_DELETION", "PENDING_DELETION"),
	DELETED("DELETED", "DELETED"),
	DISABLED("DISABLED", "DISABLED"),
	PAUSED("PAUSED", "PAUSED"),
	LIMIT_EXCEEDED("LIMIT_EXCEEDED", "LIMIT_EXCEEDED");

	private final String literal;
	private final String value;

	TemplateStatus(String literal, String value) {
		this.literal = literal;
		this.value = value;
	}

	public static TemplateStatus lookupLiteral(String literal) {
		return EnumType.lookupLiteral(TemplateStatus.class, literal);
	}

	@JsonValue
	public String getValue() {
		return value;
	}

	@Override
	public String getLiteral() {
		return literal;
	}

	@Override
	public String getName() {
		return value;
	}


}
