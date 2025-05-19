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
import java.util.Map;
import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum EntitySeries implements EnumType {
    XXX("XXX", "Unknown", 11, "xxx."),
    ENTITY("ENTITY", "Entity", 12, "Entity."),
    MODEL("MODEL", "Model", 13, "Model."),
    PRODUCT("PRODUCT", "Product", 14, "Product."),
    VALUE_TEMPLATE("VALUE_TEMPLATE", "Value Template", 15, "ValueTemplate."),
    STAGE("STAGE", "Stage", 16, "Stage."),
    RULE("RULE", "Rule", 17, "Rule."),
    SIMPLE_RULE("SIMPLE_RULE", "Simple Rule", 18, "SimpleRule."),
    COMPLEX_RULE("COMPLEX_RULE", "Complex Rule", 19, "ComplexRule."),
    SIMPLE_COMPLEX_CONDITION_RELATION(
            "SIMPLE_COMPLEX_CONDITION_RELATION",
            "Simple Complex Condition Relation",
            20,
            "SimpleComplexConditionRelation."),
    ENTITY_RULE("ENTITY_RULE", "Entity Rule", 21, "EntityRule."),
    PRODUCT_RULE("PRODUCT_RULE", "Product Rule", 22, "ProductRule."),
    VALUE_TEMPLATE_RULE("VALUE_TEMPLATE_RULE", "Value Template Rule", 23, "ValueTemplateRule.");

    private static final Map<EntitySeries, String> LEADZUMP_TOKEN_PREFIX_MAP = Map.ofEntries(
            Map.entry(XXX, XXX.getTokenPrefix()),
            Map.entry(ENTITY, "Deal."),
            Map.entry(MODEL, "Lead."),
            Map.entry(PRODUCT, "Project."),
            Map.entry(VALUE_TEMPLATE, "ValueTemplate."),
            Map.entry(STAGE, "Stage."),
            Map.entry(RULE, "Rule."),
            Map.entry(SIMPLE_RULE, SIMPLE_RULE.getTokenPrefix()),
            Map.entry(COMPLEX_RULE, COMPLEX_RULE.getTokenPrefix()),
            Map.entry(SIMPLE_COMPLEX_CONDITION_RELATION, SIMPLE_COMPLEX_CONDITION_RELATION.getTokenPrefix()),
            Map.entry(ENTITY_RULE, "DealRule."),
            Map.entry(PRODUCT_RULE, "ProjectRule."),
            Map.entry(VALUE_TEMPLATE_RULE, "ValueTemplateRule."));
    private final String literal;
    private final String displayName;
    private final int value;
    private final String tokenPrefix;

    EntitySeries(String literal, String displayName, int value, String tokenPrefix) {
        this.literal = literal;
        this.displayName = displayName;
        this.value = value;
        this.tokenPrefix = tokenPrefix;
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

    public String getTokenPrefix(String appCode) {
        if (appCode.equals("leadzump")) return LEADZUMP_TOKEN_PREFIX_MAP.get(this);

        return this.tokenPrefix;
    }
}
