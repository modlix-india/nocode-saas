package com.fincity.saas.entity.processor.enums;

import com.fincity.saas.entity.processor.dto.Owner;
import com.fincity.saas.entity.processor.dto.Product;
import com.fincity.saas.entity.processor.dto.ProductStageRule;
import com.fincity.saas.entity.processor.dto.ProductTemplate;
import com.fincity.saas.entity.processor.dto.ProductTemplateRule;
import com.fincity.saas.entity.processor.dto.Stage;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.rule.ComplexRule;
import com.fincity.saas.entity.processor.dto.rule.SimpleComplexRuleRelation;
import com.fincity.saas.entity.processor.dto.rule.SimpleRule;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorComplexRules;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorOwners;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProductStageRules;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProductTemplateRules;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProductTemplates;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProducts;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorSimpleComplexRuleRelations;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorSimpleRules;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorStages;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTickets;
import java.util.Map;
import lombok.Getter;
import org.jooq.EnumType;

@Getter
public enum EntitySeries implements EnumType {
    XXX("XXX", "Unknown", 11, "xxx.", null),
    TICKET("TICKET", "Ticket", 12, "Ticket.", EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS.getName()),
    OWNER("OWNER", "Owner", 13, "Owner.", EntityProcessorOwners.ENTITY_PROCESSOR_OWNERS.getName()),
    PRODUCT("PRODUCT", "Product", 14, "Product.", EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS.getName()),
    PRODUCT_TEMPLATE(
            "PRODUCT_TEMPLATE",
            "Product Template",
            15,
            "ProductTemplate.",
            EntityProcessorProductTemplates.ENTITY_PROCESSOR_PRODUCT_TEMPLATES.getName()),
    STAGE("STAGE", "Stage", 16, "Stage.", EntityProcessorStages.ENTITY_PROCESSOR_STAGES.getName()),
    SIMPLE_RULE(
            "SIMPLE_RULE",
            "Simple Rule",
            18,
            "SimpleRule.",
            EntityProcessorSimpleRules.ENTITY_PROCESSOR_SIMPLE_RULES.getName()),
    COMPLEX_RULE(
            "COMPLEX_RULE",
            "Complex Rule",
            19,
            "ComplexRule.",
            EntityProcessorComplexRules.ENTITY_PROCESSOR_COMPLEX_RULES.getName()),
    SIMPLE_COMPLEX_CONDITION_RELATION(
            "SIMPLE_COMPLEX_CONDITION_RELATION",
            "Simple Complex Condition Relation",
            20,
            "SimpleComplexConditionRelation.",
            EntityProcessorSimpleComplexRuleRelations.ENTITY_PROCESSOR_SIMPLE_COMPLEX_RULE_RELATIONS.getName()),
    PRODUCT_STAGE_RULE(
            "PRODUCT_STAGE_RULE",
            "Product Stage Rule",
            22,
            "ProductStageRule.",
            EntityProcessorProductStageRules.ENTITY_PROCESSOR_PRODUCT_STAGE_RULES.getName()),
    PRODUCT_TEMPLATE_RULE(
            "PRODUCT_TEMPLATE_RULE",
            "Product Template Rule",
            23,
            "ProductTemplateRule.",
            EntityProcessorProductTemplateRules.ENTITY_PROCESSOR_PRODUCT_TEMPLATE_RULES.getName());

    private static final Map<EntitySeries, String> LEADZUMP_TOKEN_PREFIX_MAP = Map.ofEntries(
            Map.entry(XXX, XXX.getTokenPrefix()),
            Map.entry(TICKET, "Deal."),
            Map.entry(OWNER, "Lead."),
            Map.entry(PRODUCT, "Project."),
            Map.entry(PRODUCT_TEMPLATE, "ProjectTemplate."),
            Map.entry(STAGE, "Stage."),
            Map.entry(SIMPLE_RULE, SIMPLE_RULE.getTokenPrefix()),
            Map.entry(COMPLEX_RULE, COMPLEX_RULE.getTokenPrefix()),
            Map.entry(SIMPLE_COMPLEX_CONDITION_RELATION, SIMPLE_COMPLEX_CONDITION_RELATION.getTokenPrefix()),
            Map.entry(PRODUCT_STAGE_RULE, "ProjectStageRule."),
            Map.entry(PRODUCT_TEMPLATE_RULE, "ProjectTemplateRule."));
    private final String literal;
    private final String displayName;
    private final int value;
    private final String tokenPrefix;
    private final String tableName;

    EntitySeries(String literal, String displayName, int value, String tokenPrefix, String tableName) {
        this.literal = literal;
        this.displayName = displayName;
        this.value = value;
        this.tokenPrefix = tokenPrefix;
        this.tableName = tableName;
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
            case SIMPLE_RULE -> SimpleRule.class;
            case COMPLEX_RULE -> ComplexRule.class;
            case SIMPLE_COMPLEX_CONDITION_RELATION -> SimpleComplexRuleRelation.class;
            case PRODUCT_STAGE_RULE -> ProductStageRule.class;
            case PRODUCT_TEMPLATE_RULE -> ProductTemplateRule.class;
        };
    }

    public String getTokenPrefix(String appCode) {
        if (appCode.equals("leadzump")) return LEADZUMP_TOKEN_PREFIX_MAP.get(this);

        return this.tokenPrefix;
    }
}
