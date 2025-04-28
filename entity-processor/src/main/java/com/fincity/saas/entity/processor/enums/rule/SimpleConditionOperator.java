package com.fincity.saas.entity.processor.enums.rule;

import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum SimpleConditionOperator implements EnumType {
	EQUALS("EQUALS"),
	LESS_THAN("LESS_THAN"),
	GREATER_THAN("GREATER_THAN"),
	LESS_THAN_EQUAL("LESS_THAN_EQUAL"),
	GREATER_THAN_EQUAL("GREATER_THAN_EQUAL"),
	IS_TRUE("IS_TRUE"),
	IS_FALSE("IS_FALSE"),
	IS_NULL("IS_NULL"),
	BETWEEN("BETWEEN"),
	IN("IN"),
	LIKE("LIKE"),
	STRING_LOOSE_EQUAL("STRING_LOOSE_EQUAL"),
	MATCH("MATCH"),
	MATCH_ALL("MATCH_ALL"),
	TEXT_SEARCH("TEXT_SEARCH");

	private final String literal;

    SimpleConditionOperator(String literal) {
        this.literal = literal;
    }

    public static SimpleConditionOperator lookupLiteral(String literal) {
        return EnumType.lookupLiteral(SimpleConditionOperator.class, literal);
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
