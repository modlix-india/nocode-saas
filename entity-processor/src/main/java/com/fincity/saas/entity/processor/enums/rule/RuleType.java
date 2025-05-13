package com.fincity.saas.entity.processor.enums.rule;

import org.jooq.EnumType;

import com.fincity.saas.entity.processor.enums.EntitySeries;

import lombok.Getter;

@Getter
public enum RuleType implements EnumType {
    ENTITY_ASSIGNMENT("ENTITY_ASSIGNMENT", EntitySeries.PRODUCT, EntitySeries.ENTITY),
    STAGE_ENTITY_ASSIGNMENT("STAGE_ENTITY_ASSIGNMENT", EntitySeries.PRODUCT, EntitySeries.ENTITY),
    PRODUCT_ASSIGNMENT("PRODUCT_ASSIGNMENT", EntitySeries.PRODUCT, EntitySeries.PRODUCT);

    private final String literal;
    private final EntitySeries createdUnder;
    private final EntitySeries createdFor;

    RuleType(String literal, EntitySeries createdUnder, EntitySeries createdFor) {
        this.literal = literal;
        this.createdUnder = createdUnder;
        this.createdFor = createdFor;
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
