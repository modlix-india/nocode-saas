package com.fincity.saas.entity.processor.enums;

import java.util.EnumMap;
import java.util.Map;

import org.jooq.EnumType;
import org.jooq.Table;

import com.fincity.saas.entity.processor.jooq.Tables;

import lombok.Getter;

@Getter
public enum EntitySeries implements EnumType {
    XXX("XXX", "Unknown", 11, "xxx"),
    TICKET("TICKET", "Ticket", 12, "Ticket"),
    OWNER("OWNER", "Owner", 13, "Owner"),
    PRODUCT("PRODUCT", "Product", 14, "Product"),
    PRODUCT_TEMPLATE("PRODUCT_TEMPLATE", "Product Template", 15, "ProductTemplate"),
    PRODUCT_COMM("PRODUCT_COMM", "Product Communications", 16, "ProductComm"),
    STAGE("STAGE", "Stage", 17, "Stage"),
    SIMPLE_RULE("SIMPLE_RULE", "Simple Rule", 18, "SimpleRule"),
    COMPLEX_RULE("COMPLEX_RULE", "Complex Rule", 19, "ComplexRule"),
    SIMPLE_COMPLEX_CONDITION_RELATION(
            "SIMPLE_COMPLEX_CONDITION_RELATION",
            "Simple Complex Condition Relation",
            20,
            "SimpleComplexConditionRelation"),
    PRODUCT_STAGE_RULE("PRODUCT_STAGE_RULE", "Product Stage Rule", 21, "ProductStageRule"),
    PRODUCT_TEMPLATE_RULE("PRODUCT_TEMPLATE_RULE", "Product Template Rule", 22, "ProductTemplateRule"),
    TASK("TASK", "Task", 23, "Task"),
    TASK_TYPE("TASK_TYPE", "TaskType", 24, "TaskType"),
    NOTE("NOTE", "Note", 25, "Note"),
    ACTIVITY("ACTIVITY", "Activity", 26, "Activity"),
    CAMPAIGN("CAMPAIGN", "Campaign", 27, "Campaign"),
    PARTNER("PARTNER", "Partner", 28, "Partner"),
    PRODUCT_TEMPLATE_WALK_IN_FORMS(
            "PRODUCT_TEMPLATE_WALK_IN_FORMS", "Product Template Walk In Forms", 29, "ProductTemplateWalkInForm"),
    PRODUCT_WALK_IN_FORMS("PRODUCT_WALK_IN_FORMS", "Product Walk In Forms", 30, "ProductWalkInForms");

    private static final Map<EntitySeries, String> LEADZUMP_ENTITY_MAP = new EnumMap<>(EntitySeries.class);

    static {
        LEADZUMP_ENTITY_MAP.put(XXX, XXX.getPrefix());
        LEADZUMP_ENTITY_MAP.put(TICKET, "Deal");
        LEADZUMP_ENTITY_MAP.put(OWNER, "Lead");
        LEADZUMP_ENTITY_MAP.put(PRODUCT, "Project");
        LEADZUMP_ENTITY_MAP.put(PRODUCT_COMM, "ProjectComm");
        LEADZUMP_ENTITY_MAP.put(PRODUCT_TEMPLATE, "ProjectTemplate");
        LEADZUMP_ENTITY_MAP.put(STAGE, "Stage");
        LEADZUMP_ENTITY_MAP.put(SIMPLE_RULE, SIMPLE_RULE.getPrefix());
        LEADZUMP_ENTITY_MAP.put(COMPLEX_RULE, COMPLEX_RULE.getPrefix());
        LEADZUMP_ENTITY_MAP.put(SIMPLE_COMPLEX_CONDITION_RELATION, SIMPLE_COMPLEX_CONDITION_RELATION.getPrefix());
        LEADZUMP_ENTITY_MAP.put(PRODUCT_STAGE_RULE, "ProjectStageRule");
        LEADZUMP_ENTITY_MAP.put(PRODUCT_TEMPLATE_RULE, "ProjectTemplateRule");
        LEADZUMP_ENTITY_MAP.put(TASK, "Task");
        LEADZUMP_ENTITY_MAP.put(TASK_TYPE, "TaskType");
        LEADZUMP_ENTITY_MAP.put(NOTE, "Note");
        LEADZUMP_ENTITY_MAP.put(ACTIVITY, "Activity");
        LEADZUMP_ENTITY_MAP.put(CAMPAIGN, "Campaign");
        LEADZUMP_ENTITY_MAP.put(PARTNER, "Partner");
        LEADZUMP_ENTITY_MAP.put(PRODUCT_TEMPLATE_WALK_IN_FORMS, "ProductTemplateWalkInForms");
        LEADZUMP_ENTITY_MAP.put(PRODUCT_WALK_IN_FORMS, "ProductWalkInForms");
    }

    private final String literal;
    private final String displayName;
    private final int value;
    private final String prefix;

    EntitySeries(String literal, String displayName, int value, String prefix) {
        this.literal = literal;
        this.displayName = displayName;
        this.value = value;
        this.prefix = prefix;
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

    public Table<?> getTable() {
        return TableHolder.TABLE_MAP.get(this);
    }

    public String getTokenPrefix(String appCode) {
        if (appCode.equals("leadzump")) return LEADZUMP_ENTITY_MAP.get(this) + ".";
        return this.prefix + ".";
    }

    public String getPrefix(String appCode) {
        if (appCode.equals("leadzump")) return LEADZUMP_ENTITY_MAP.get(this);
        return this.prefix;
    }

    private static class TableHolder {
        private static final Map<EntitySeries, Table<?>> TABLE_MAP = new EnumMap<>(EntitySeries.class);

        static {
            TABLE_MAP.put(TICKET, Tables.ENTITY_PROCESSOR_TICKETS);
            TABLE_MAP.put(OWNER, Tables.ENTITY_PROCESSOR_OWNERS);
            TABLE_MAP.put(PRODUCT, Tables.ENTITY_PROCESSOR_PRODUCTS);
            TABLE_MAP.put(PRODUCT_TEMPLATE, Tables.ENTITY_PROCESSOR_PRODUCT_TEMPLATES);
            TABLE_MAP.put(PRODUCT_COMM, Tables.ENTITY_PROCESSOR_PRODUCT_COMMS);
            TABLE_MAP.put(STAGE, Tables.ENTITY_PROCESSOR_STAGES);
            TABLE_MAP.put(SIMPLE_RULE, Tables.ENTITY_PROCESSOR_SIMPLE_RULES);
            TABLE_MAP.put(COMPLEX_RULE, Tables.ENTITY_PROCESSOR_COMPLEX_RULES);
            TABLE_MAP.put(SIMPLE_COMPLEX_CONDITION_RELATION, Tables.ENTITY_PROCESSOR_SIMPLE_COMPLEX_RULE_RELATIONS);
            TABLE_MAP.put(PRODUCT_STAGE_RULE, Tables.ENTITY_PROCESSOR_PRODUCT_STAGE_RULES);
            TABLE_MAP.put(PRODUCT_TEMPLATE_RULE, Tables.ENTITY_PROCESSOR_PRODUCT_TEMPLATE_RULES);
            TABLE_MAP.put(TASK, Tables.ENTITY_PROCESSOR_TASKS);
            TABLE_MAP.put(TASK_TYPE, Tables.ENTITY_PROCESSOR_TASK_TYPES);
            TABLE_MAP.put(NOTE, Tables.ENTITY_PROCESSOR_NOTES);
            TABLE_MAP.put(ACTIVITY, Tables.ENTITY_PROCESSOR_ACTIVITIES);
            TABLE_MAP.put(CAMPAIGN, Tables.ENTITY_PROCESSOR_CAMPAIGNS);
            TABLE_MAP.put(PARTNER, Tables.ENTITY_PROCESSOR_PARTNERS);
            TABLE_MAP.put(PRODUCT_TEMPLATE_WALK_IN_FORMS, Tables.ENTITY_PROCESSOR_PRODUCT_TEMPLATE_WALK_IN_FORMS);
            TABLE_MAP.put(PRODUCT_WALK_IN_FORMS, Tables.ENTITY_PROCESSOR_PRODUCT_WALK_IN_FORMS);
        }
    }
}
