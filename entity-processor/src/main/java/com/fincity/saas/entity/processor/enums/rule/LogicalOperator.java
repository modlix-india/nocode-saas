package com.fincity.saas.entity.processor.enums.rule;

import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum LogicalOperator implements EnumType {
    AND("AND"),
    OR("OR");

    private final String literal;

    LogicalOperator(String literal) {
        this.literal = literal;
    }

    public static LogicalOperator lookupLiteral(String literal) {
        return EnumType.lookupLiteral(LogicalOperator.class, literal);
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
