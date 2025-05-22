package com.fincity.saas.entity.processor.enums;

import com.fincity.saas.entity.processor.dto.Owner;
import com.fincity.saas.entity.processor.dto.Product;
import com.fincity.saas.entity.processor.dto.ProductStageRule;
import com.fincity.saas.entity.processor.dto.ProductTemplate;
import com.fincity.saas.entity.processor.dto.ProductTemplateRule;
import com.fincity.saas.entity.processor.dto.Stage;
import com.fincity.saas.entity.processor.dto.Ticket;
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
    TICKET("TICKET", "Ticket", 12, "Ticket."),
    OWNER("OWNER", "Owner", 13, "Owner."),
    PRODUCT("PRODUCT", "Product", 14, "Product."),
    PRODUCT_TEMPLATE("PRODUCT_TEMPLATE", "Product Template", 15, "ProductTemplate."),
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
    PRODUCT_STAGE_RULE("PRODUCT_STAGE_RULE", "Product Stage Rule", 22, "ProductStageRule."),
    PRODUCT_TEMPLATE_RULE("PRODUCT_TEMPLATE_RULE", "Product Template Rule", 23, "ProductTemplateRule.");

    private static final Map<EntitySeries, String> LEADZUMP_TOKEN_PREFIX_MAP = Map.ofEntries(
            Map.entry(XXX, XXX.getTokenPrefix()),
            Map.entry(TICKET, "Deal."),
            Map.entry(OWNER, "Lead."),
            Map.entry(PRODUCT, "Project."),
            Map.entry(PRODUCT_TEMPLATE, "ProjectTemplate."),
            Map.entry(STAGE, "Stage."),
            Map.entry(RULE, "Rule."),
            Map.entry(SIMPLE_RULE, SIMPLE_RULE.getTokenPrefix()),
            Map.entry(COMPLEX_RULE, COMPLEX_RULE.getTokenPrefix()),
            Map.entry(SIMPLE_COMPLEX_CONDITION_RELATION, SIMPLE_COMPLEX_CONDITION_RELATION.getTokenPrefix()),
            Map.entry(ENTITY_RULE, "DealRule."),
            Map.entry(PRODUCT_STAGE_RULE, "ProjectStageRule."),
            Map.entry(PRODUCT_TEMPLATE_RULE, "ProjectTemplateRule."));
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
            case TICKET -> Ticket.class;
            case OWNER -> Owner.class;
            case PRODUCT -> Product.class;
            case PRODUCT_TEMPLATE -> ProductTemplate.class;
            case STAGE -> Stage.class;
            case RULE -> Rule.class;
            case SIMPLE_RULE -> SimpleRule.class;
            case COMPLEX_RULE -> ComplexRule.class;
            case SIMPLE_COMPLEX_CONDITION_RELATION -> SimpleComplexRuleRelation.class;
            case ENTITY_RULE -> null;
            case PRODUCT_STAGE_RULE -> ProductStageRule.class;
            case PRODUCT_TEMPLATE_RULE -> ProductTemplateRule.class;
        };
    }

    public String getTokenPrefix(String appCode) {
        if (appCode.equals("leadzump")) return LEADZUMP_TOKEN_PREFIX_MAP.get(this);

        return this.tokenPrefix;
    }
}
