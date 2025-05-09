package com.fincity.saas.entity.processor.enums;

import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum EntitySeries implements EnumType {
    XXX("XXX", 11),
    ENTITY("ENTITY", 12),
    MODEL("MODEL", 13),
    PRODUCT("PRODUCT", 14),
    VALUE_TEMPLATE("VALUE_TEMPLATE", 15),
    SOURCE("SOURCE", 16),
    STAGE("STAGE", 17),
    RULE("RULE", 18),
    SIMPLE_CONDITION("SIMPLE_CONDITION", 19),
    COMPLEX_CONDITION("COMPLEX_CONDITION", 20),
    SIMPLE_COMPLEX_CONDITION_RELATION("SIMPLE_COMPLEX_CONDITION_RELATION", 21),
    ENTITY_RULE("ENTITY_RULE", 22),
    PRODUCT_RULE("PRODUCT_RULE", 23),
    PRODUCT_RULE_CONFIG("PRODUCT_RULE_CONFIG", 24);

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
