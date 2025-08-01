package com.fincity.saas.message.enums.message.provider.whatsapp.business;

import org.jooq.EnumType;

import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;

@Getter
public enum SubCategory implements EnumType {
	ORDER_DETAILS("ORDER_DETAILS", "ORDER_DETAILS"),
	ORDER_STATUS("ORDER_STATUS", "ORDER_STATUS");

	private final String literal;
	private final String value;

	SubCategory(String literal, String value) {
		this.literal = literal;
		this.value = value;
	}

	public static SubCategory lookupLiteral(String literal) {
		return EnumType.lookupLiteral(SubCategory.class, literal);
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
