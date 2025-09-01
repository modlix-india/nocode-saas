package com.fincity.saas.entity.processor.enums;

import com.fincity.saas.entity.processor.dto.Activity;
import com.fincity.saas.entity.processor.dto.Owner;
import com.fincity.saas.entity.processor.dto.Partner;
import com.fincity.saas.entity.processor.dto.Product;
import com.fincity.saas.entity.processor.dto.ProductStageRule;
import com.fincity.saas.entity.processor.dto.ProductTemplate;
import com.fincity.saas.entity.processor.dto.ProductTemplateRule;
import com.fincity.saas.entity.processor.dto.Stage;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.content.Note;
import com.fincity.saas.entity.processor.dto.content.Task;
import com.fincity.saas.entity.processor.dto.content.TaskType;
import com.fincity.saas.entity.processor.dto.rule.ComplexRule;
import com.fincity.saas.entity.processor.dto.rule.SimpleComplexRuleRelation;
import com.fincity.saas.entity.processor.dto.rule.SimpleRule;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorComplexRules;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorNotes;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorOwners;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorPartners;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProductStageRules;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProductTemplateRules;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProductTemplates;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProducts;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorSimpleComplexRuleRelations;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorSimpleRules;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorStages;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTaskTypes;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTasks;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTickets;
import java.util.Map;
import lombok.Getter;
import org.jooq.EnumType;
import org.jooq.Table;

@Getter
public enum EntitySeries implements EnumType {
    XXX("XXX", "Unknown", 11, "xxx", null),
    TICKET("TICKET", "Ticket", 12, "Ticket", EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS),
    OWNER("OWNER", "Owner", 13, "Owner", EntityProcessorOwners.ENTITY_PROCESSOR_OWNERS),
    PRODUCT("PRODUCT", "Product", 14, "Product", EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS),
    PRODUCT_TEMPLATE(
            "PRODUCT_TEMPLATE",
            "Product Template",
            15,
            "ProductTemplate",
            EntityProcessorProductTemplates.ENTITY_PROCESSOR_PRODUCT_TEMPLATES),
    STAGE("STAGE", "Stage", 15, "Stage", EntityProcessorStages.ENTITY_PROCESSOR_STAGES),
    SIMPLE_RULE(
            "SIMPLE_RULE", "Simple Rule", 16, "SimpleRule", EntityProcessorSimpleRules.ENTITY_PROCESSOR_SIMPLE_RULES),
    COMPLEX_RULE(
            "COMPLEX_RULE",
            "Complex Rule",
            17,
            "ComplexRule",
            EntityProcessorComplexRules.ENTITY_PROCESSOR_COMPLEX_RULES),
    SIMPLE_COMPLEX_CONDITION_RELATION(
            "SIMPLE_COMPLEX_CONDITION_RELATION",
            "Simple Complex Condition Relation",
            18,
            "SimpleComplexConditionRelation",
            EntityProcessorSimpleComplexRuleRelations.ENTITY_PROCESSOR_SIMPLE_COMPLEX_RULE_RELATIONS),
    PRODUCT_STAGE_RULE(
            "PRODUCT_STAGE_RULE",
            "Product Stage Rule",
            19,
            "ProductStageRule",
            EntityProcessorProductStageRules.ENTITY_PROCESSOR_PRODUCT_STAGE_RULES),
    PRODUCT_TEMPLATE_RULE(
            "PRODUCT_TEMPLATE_RULE",
            "Product Template Rule",
            20,
            "ProductTemplateRule",
            EntityProcessorProductTemplateRules.ENTITY_PROCESSOR_PRODUCT_TEMPLATE_RULES),
    TASK("TASK", "Task", 21, "Task", EntityProcessorTasks.ENTITY_PROCESSOR_TASKS),
    TASK_TYPE("TASK_TYPE", "TaskType", 22, "TaskType", EntityProcessorTaskTypes.ENTITY_PROCESSOR_TASK_TYPES),
    NOTE("NOTE", "Note", 23, "Note", EntityProcessorNotes.ENTITY_PROCESSOR_NOTES),
    ACTIVITY("ACTIVITY", "Activity", 24, "Activity", null),
    CAMPAIGN("CAMPAIGN", "Campaign", 25, "Campaign", EntityProcessorCampaigns.ENTITY_PROCESSOR_CAMPAIGNS),
    PARTNER("PARTNER", "Partner", 26, "Partner", EntityProcessorPartners.ENTITY_PROCESSOR_PARTNERS);

    private static final Map<EntitySeries, String> LEADZUMP_ENTITY_MAP = Map.ofEntries(
            Map.entry(XXX, XXX.getPrefix()),
            Map.entry(TICKET, "Deal"),
            Map.entry(OWNER, "Lead"),
            Map.entry(PRODUCT, "Project"),
            Map.entry(PRODUCT_TEMPLATE, "ProjectTemplate"),
            Map.entry(STAGE, "Stage"),
            Map.entry(SIMPLE_RULE, SIMPLE_RULE.getPrefix()),
            Map.entry(COMPLEX_RULE, COMPLEX_RULE.getPrefix()),
            Map.entry(SIMPLE_COMPLEX_CONDITION_RELATION, SIMPLE_COMPLEX_CONDITION_RELATION.getPrefix()),
            Map.entry(PRODUCT_STAGE_RULE, "ProjectStageRule"),
            Map.entry(PRODUCT_TEMPLATE_RULE, "ProjectTemplateRule"),
            Map.entry(TASK, "Task"),
            Map.entry(TASK_TYPE, "TaskType"),
            Map.entry(NOTE, "Note"),
            Map.entry(ACTIVITY, "activity"),
            Map.entry(PARTNER, "Partner"));

    private final String literal;
    private final String displayName;
    private final int value;
    private final String prefix;
    private final Table<?> table;

    EntitySeries(String literal, String displayName, int value, String prefix, Table<?> table) {
        this.literal = literal;
        this.displayName = displayName;
        this.value = value;
        this.prefix = prefix;
        this.table = table;
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
            case TASK -> Task.class;
            case TASK_TYPE -> TaskType.class;
            case NOTE -> Note.class;
            case ACTIVITY -> Activity.class;
            case CAMPAIGN -> Campaign.class;
            case PARTNER -> Partner.class;
        };
    }

    public String getTokenPrefix(String appCode) {
        if (appCode.equals("leadzump")) return LEADZUMP_ENTITY_MAP.get(this) + ".";
        return this.prefix + ".";
    }

    public String getPrefix(String appCode) {
        if (appCode.equals("leadzump")) return LEADZUMP_ENTITY_MAP.get(this);
        return this.prefix;
    }
}
