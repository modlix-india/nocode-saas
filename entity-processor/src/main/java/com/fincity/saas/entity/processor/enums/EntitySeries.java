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
    STAGE("STAGE", 16),
    RULE("RULE", 17),
    SIMPLE_CONDITION("SIMPLE_CONDITION", 18),
    COMPLEX_CONDITION("COMPLEX_CONDITION", 19),
    SIMPLE_COMPLEX_CONDITION_RELATION("SIMPLE_COMPLEX_CONDITION_RELATION", 20),
    ENTITY_RULE("ENTITY_RULE", 21),
    PRODUCT_RULE("PRODUCT_RULE", 22),
    PRODUCT_RULE_CONFIG("PRODUCT_RULE_CONFIG", 23),
    ;

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
