package com.fincity.saas.entity.processor.enums;

import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum EntitySeries implements EnumType {
    XXX("XXX", 11),
    ENTITY("ENTITY", 12),
    MODEL("MODEL", 13),
    PRODUCT("PRODUCT", 14),
    SOURCE("SOURCE", 15),
    STAGE("STAGE", 16);

    private final String literal;
    private final int value;

    EntitySeries(String literal, int value) {
        this.literal = literal;
        this.value = value;
    }

    public static EntitySeries lookupLiteral(String literal) {
        return EnumType.lookupLiteral(EntitySeries.class, literal);
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
