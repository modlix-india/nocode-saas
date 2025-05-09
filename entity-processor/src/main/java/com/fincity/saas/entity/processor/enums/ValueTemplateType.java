package com.fincity.saas.entity.processor.enums;

import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum ValueTemplateType implements EnumType {
    ENTITY("ENTITY", true),
    PRODUCT("PRODUCT", false);

    private final String literal;

    // if ValueTemplateType is app level then value template will not be associated with any Database entity.
    private final boolean isAppLevel;

    ValueTemplateType(String literal, boolean isAppLevel) {
        this.literal = literal;
        this.isAppLevel = isAppLevel;
    }

    public static ValueTemplateType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(ValueTemplateType.class, literal);
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
