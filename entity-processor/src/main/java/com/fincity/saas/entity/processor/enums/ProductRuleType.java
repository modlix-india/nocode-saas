package com.fincity.saas.entity.processor.enums;

import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum ProductRuleType implements EnumType {
    DEAL("DEAL"),
    STAGE("STAGE");

    private final String literal;

    ProductRuleType(String literal) {
        this.literal = literal;
    }

    public static ProductRuleType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(ProductRuleType.class, literal);
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
