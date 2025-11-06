package com.fincity.saas.entity.processor.enums;

import java.util.Map;

import org.jooq.EnumType;
import org.jooq.Table;

import com.fincity.saas.entity.processor.jooq.EntityProcessor;

import lombok.Getter;

@Getter
public enum EntitySeries implements EnumType {
    XXX("XXX", "Unknown", 11, "xxx", null),
    TICKET("TICKET", "Ticket", 12, "Ticket", EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_TICKETS),
    OWNER("OWNER", "Owner", 13, "Owner", EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_OWNERS),
    PRODUCT("PRODUCT", "Product", 14, "Product", EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_PRODUCTS),
    PRODUCT_TEMPLATE(
            "PRODUCT_TEMPLATE",
            "Product Template",
            15,
            "ProductTemplate",
            EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_PRODUCT_TEMPLATES),
    PRODUCT_COMM(
            "PRODUCT_COMM",
            "Product Communications",
            16,
            "ProductComm",
            EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_PRODUCT_COMMS),
    STAGE("STAGE", "Stage", 17, "Stage", EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_STAGES),
    TICKET_C_USER_DISTRIBUTION(
            "TICKET_C_USER_DISTRIBUTION",
            "Ticket Creation User Distribution",
            18,
            "TicketCUserDistribution",
            EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_TICKET_C_USER_DISTRIBUTIONS),
	TICKET_RU_USER_DISTRIBUTION(
			"TICKET_RU_USER_DISTRIBUTION",
			"Ticket Read Update User Distribution",
			19,
			"TicketRUUserDistribution",
			EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_TICKET_RU_USER_DISTRIBUTIONS),
	ENTITY_PROCESSOR_PRODUCT_TICKET_C_RULES(
			"ENTITY_PROCESSOR_PRODUCT_TICKET_C_RULES",
			"Product Ticket Creation Rules",
			20,
			"ProductTicketCRule",
			EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_PRODUCT_TICKET_C_RULES),
	ENTITY_PROCESSOR_PRODUCT_TICKET_RU_RULES(
			"ENTITY_PROCESSOR_PRODUCT_TICKET_RU_RULES",
			"Product Ticket Read Update Rules",
			21,
			"ProductTicketRURule",
			EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_PRODUCT_TICKET_RU_RULES),
    TASK("TASK", "Task", 22, "Task", EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_TASKS),
    TASK_TYPE("TASK_TYPE", "Task Type", 23, "TaskType", EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_TASK_TYPES),
    NOTE("NOTE", "Note", 24, "Note", EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_NOTES),
    ACTIVITY("ACTIVITY", "Activity", 25, "Activity", null),
    CAMPAIGN("CAMPAIGN", "Campaign", 26, "Campaign", EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_CAMPAIGNS),
    PARTNER("PARTNER", "Partner", 27, "Partner", EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_PARTNERS),
    PRODUCT_TEMPLATE_WALK_IN_FORMS(
            "PRODUCT_TEMPLATE_WALK_IN_FORMS",
            "Product Template Walk In Forms",
            28,
            "ProductTemplateWalkInForm",
            EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_PRODUCT_TEMPLATE_WALK_IN_FORMS),
    PRODUCT_WALK_IN_FORMS(
            "PRODUCT_WALK_IN_FORMS",
            "Product Walk In Forms",
            29,
            "ProductWalkInForms",
            EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_PRODUCT_WALK_IN_FORMS);

    private static final Map<EntitySeries, String> LEADZUMP_ENTITY_MAP = Map.ofEntries(
            Map.entry(XXX, XXX.getPrefix()),
            Map.entry(TICKET, "Deal"),
            Map.entry(OWNER, "Lead"),
            Map.entry(PRODUCT, "Project"),
            Map.entry(PRODUCT_COMM, "ProjectComm"),
            Map.entry(PRODUCT_TEMPLATE, "ProjectTemplate"),
            Map.entry(STAGE, "Stage"),
            Map.entry(TASK, "Task"),
            Map.entry(TASK_TYPE, "TaskType"),
            Map.entry(NOTE, "Note"),
            Map.entry(ACTIVITY, "Activity"),
            Map.entry(CAMPAIGN, "Campaign"),
            Map.entry(PARTNER, "Partner"),
            Map.entry(PRODUCT_TEMPLATE_WALK_IN_FORMS, "ProductTemplateWalkInForms"),
            Map.entry(PRODUCT_WALK_IN_FORMS, "ProductWalkInForms"));

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

    public String getTokenPrefix(String appCode) {
        if (appCode.equals("leadzump")) return LEADZUMP_ENTITY_MAP.get(this) + ".";
        return this.prefix + ".";
    }

    public String getPrefix(String appCode) {
        if (appCode.equals("leadzump")) return LEADZUMP_ENTITY_MAP.get(this);
        return this.prefix;
    }
}
