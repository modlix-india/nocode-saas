package com.fincity.saas.entity.processor.enums;

import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum ValueTemplateType implements EnumType {
    ENTITY("ENTITY", "Entity", true),
    PRODUCT("PRODUCT", "Product", false);

    private final String literal;
    private final String displayName;

    // if ValueTemplateType is app level then value template will not be associated with any Database entity.
    private final boolean isAppLevel;

    ValueTemplateType(String literal, String displayName, boolean isAppLevel) {
        this.literal = literal;
        this.displayName = displayName;
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
