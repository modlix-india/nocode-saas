package com.fincity.saas.entity.processor.enums;

import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum ProductTemplateType implements EnumType {
    GENERAL("GENERAL", "General");

    private final String literal;
    private final String displayName;

    ProductTemplateType(String literal, String displayName) {
        this.literal = literal;
        this.displayName = displayName;
    }

    public static ProductTemplateType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(ProductTemplateType.class, literal);
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
