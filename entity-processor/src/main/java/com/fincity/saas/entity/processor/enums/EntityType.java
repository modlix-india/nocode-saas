package com.fincity.saas.entity.processor.enums;

import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum EntityType implements EnumType {
    XXX("XXX"),
    ENTITY("ENTITY"),
    MODEL("MODEL"),
    PRODUCT("PRODUCT"),
    SOURCE("SOURCE"),
    STAGE("STAGE");

    private final String literal;

    EntityType(String literal) {
        this.literal = literal;
    }

    public static EntityType lookupLiteral(String literal) {
        return EnumType.lookupLiteral(EntityType.class, literal);
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
