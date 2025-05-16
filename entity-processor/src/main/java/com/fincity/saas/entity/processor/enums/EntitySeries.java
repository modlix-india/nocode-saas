package com.fincity.saas.entity.processor.enums;

import com.fincity.saas.entity.processor.dto.Entity;
import com.fincity.saas.entity.processor.dto.Model;
import com.fincity.saas.entity.processor.dto.Product;
import com.fincity.saas.entity.processor.dto.ProductRule;
import com.fincity.saas.entity.processor.dto.Stage;
import com.fincity.saas.entity.processor.dto.ValueTemplate;
import com.fincity.saas.entity.processor.dto.ValueTemplateRule;
import com.fincity.saas.entity.processor.dto.rule.ComplexRule;
import com.fincity.saas.entity.processor.dto.rule.Rule;
import com.fincity.saas.entity.processor.dto.rule.SimpleComplexRuleRelation;
import com.fincity.saas.entity.processor.dto.rule.SimpleRule;
import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum EntitySeries implements EnumType {
    XXX("XXX", "Unknown", 11),
    ENTITY("ENTITY", "Entity", 12),
    MODEL("MODEL", "Model", 13),
    PRODUCT("PRODUCT", "Product", 14),
    VALUE_TEMPLATE("VALUE_TEMPLATE", "Value Template", 15),
    STAGE("STAGE", "Stage", 16),
    RULE("RULE", "Rule", 17),
    SIMPLE_RULE("SIMPLE_RULE", "Simple Rule", 18),
    COMPLEX_RULE("COMPLEX_RULE", "Complex Rule", 19),
    SIMPLE_COMPLEX_CONDITION_RELATION("SIMPLE_COMPLEX_CONDITION_RELATION", "Simple Complex Condition Relation", 20),
    ENTITY_RULE("ENTITY_RULE", "Entity Rule", 21),
    PRODUCT_RULE("PRODUCT_RULE", "Product Rule", 22),
    VALUE_TEMPLATE_RULE("VALUE_TEMPLATE_RULE", "Value Template Rule", 23);

    private final String literal;
    private final String displayName;
    private final int value;

    EntitySeries(String literal, String displayName, int value) {
        this.literal = literal;
        this.displayName = displayName;
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
        return this.displayName;
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
            case SIMPLE_RULE -> SimpleRule.class;
            case COMPLEX_RULE -> ComplexRule.class;
            case SIMPLE_COMPLEX_CONDITION_RELATION -> SimpleComplexRuleRelation.class;
            case ENTITY_RULE -> null;
            case PRODUCT_RULE -> ProductRule.class;
            case VALUE_TEMPLATE_RULE -> ValueTemplateRule.class;
        };
    }
}
