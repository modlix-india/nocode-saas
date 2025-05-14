package com.fincity.saas.entity.processor.enums;

import com.fincity.saas.entity.processor.dto.Entity;
import com.fincity.saas.entity.processor.dto.Model;
import com.fincity.saas.entity.processor.dto.Product;
import com.fincity.saas.entity.processor.dto.ProductRule;
import com.fincity.saas.entity.processor.dto.Stage;
import com.fincity.saas.entity.processor.dto.ValueTemplate;
import com.fincity.saas.entity.processor.dto.rule.ComplexRule;
import com.fincity.saas.entity.processor.dto.rule.Rule;
import com.fincity.saas.entity.processor.dto.rule.SimpleComplexRuleRelation;
import com.fincity.saas.entity.processor.dto.rule.SimpleRule;
import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum EntitySeries implements EnumType {
    XXX("XXX", 11),
    ENTITY("ENTITY", 12),
    MODEL("MODEL", 13),
    PRODUCT("PRODUCT", 14),
    VALUE_TEMPLATE("VALUE_TEMPLATE", 15),
    STAGE("STAGE", 16),
    RULE("RULE", 17),
    SIMPLE_CONDITION("SIMPLE_CONDITION", 18),
    COMPLEX_CONDITION("COMPLEX_CONDITION", 19),
    SIMPLE_COMPLEX_CONDITION_RELATION("SIMPLE_COMPLEX_CONDITION_RELATION", 20),
    ENTITY_RULE("ENTITY_RULE", 21),
    PRODUCT_RULE("PRODUCT_RULE", 22);

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

    public Class<?> getDtoClass() {
        return switch (this) {
            case XXX -> null;
            case ENTITY -> Entity.class;
            case MODEL -> Model.class;
            case PRODUCT -> Product.class;
            case VALUE_TEMPLATE -> ValueTemplate.class;
            case STAGE -> Stage.class;
            case RULE -> Rule.class;
            case SIMPLE_CONDITION -> SimpleRule.class;
            case COMPLEX_CONDITION -> ComplexRule.class;
            case SIMPLE_COMPLEX_CONDITION_RELATION -> SimpleComplexRuleRelation.class;
            case ENTITY_RULE -> null;
            case PRODUCT_RULE -> ProductRule.class;
        };
    }
}
