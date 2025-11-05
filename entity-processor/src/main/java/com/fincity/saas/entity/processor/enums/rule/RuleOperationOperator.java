package com.fincity.saas.entity.processor.enums.rule;

import org.jooq.EnumType;

import lombok.Getter;

@Getter
public enum RuleOperationOperator implements EnumType {
    CREATE("EQUALS"),
    READ("LESS_THAN"),
    UPDATE("GREATER_THAN");

    private final String literal;

    RuleOperationOperator(String literal) {
        this.literal = literal;
    }

    public static RuleOperationOperator lookupLiteral(String literal) {
        return EnumType.lookupLiteral(RuleOperationOperator.class, literal);
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
