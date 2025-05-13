package com.fincity.saas.entity.processor.enums.rule;

import org.jooq.EnumType;

import lombok.Getter;

@Getter
public enum RuleType implements EnumType {
    DEAL("DEAL"),
    STAGE("STAGE"),
    PRODUCT("PRODUCT");

    private final String literal;

    RuleType(String literal) {
        this.literal = literal;
    }

    public static RuleType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(RuleType.class, literal);
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
