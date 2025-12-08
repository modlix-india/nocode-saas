package com.fincity.saas.entity.processor.enums;

import com.fincity.saas.entity.processor.dto.Activity;
import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.dto.Owner;
import com.fincity.saas.entity.processor.dto.Partner;
import com.fincity.saas.entity.processor.dto.Stage;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.content.Note;
import com.fincity.saas.entity.processor.dto.content.Task;
import com.fincity.saas.entity.processor.dto.content.TaskType;
import com.fincity.saas.entity.processor.dto.form.ProductTemplateWalkInForm;
import com.fincity.saas.entity.processor.dto.form.ProductWalkInForm;
import com.fincity.saas.entity.processor.dto.product.Product;
import com.fincity.saas.entity.processor.dto.product.ProductComm;
import com.fincity.saas.entity.processor.dto.product.ProductTemplate;
import com.fincity.saas.entity.processor.dto.product.ProductTicketCRule;
import com.fincity.saas.entity.processor.dto.product.ProductTicketRuRule;
import com.fincity.saas.entity.processor.dto.rule.TicketCUserDistribution;
import com.fincity.saas.entity.processor.dto.rule.TicketDuplicationRule;
import com.fincity.saas.entity.processor.dto.rule.TicketPeDuplicationRule;
import com.fincity.saas.entity.processor.dto.rule.TicketRuUserDistribution;
import com.fincity.saas.entity.processor.jooq.EntityProcessor;
import java.util.EnumMap;
import java.util.Map;
import lombok.Getter;
import org.jooq.EnumType;
import org.jooq.Table;

@Getter
public enum EntitySeries implements EnumType {
    XXX("XXX", "Unknown", 11, "xxx"),
    TICKET("TICKET", "Ticket", 12, "Ticket"),
    OWNER("OWNER", "Owner", 13, "Owner"),
    PRODUCT("PRODUCT", "Product", 14, "Product"),
    PRODUCT_TEMPLATE("PRODUCT_TEMPLATE", "Product Template", 15, "ProductTemplate"),
    PRODUCT_COMM("PRODUCT_COMM", "Product Communications", 16, "ProductComm"),
    STAGE("STAGE", "Stage", 17, "Stage"),
    TICKET_C_USER_DISTRIBUTION(
            "TICKET_C_USER_DISTRIBUTION", "Ticket Creation User Distribution", 18, "TicketCUserDistribution"),
    TICKET_RU_USER_DISTRIBUTION(
            "TICKET_RU_USER_DISTRIBUTION", "Ticket Read Update User Distribution", 19, "TicketRuUserDistribution"),
    PRODUCT_TICKET_C_RULE("PRODUCT_TICKET_C_RULE", "Product Ticket Creation Rule", 20, "ProductTicketCRule"),
    PRODUCT_TICKET_RU_RULE("PRODUCT_TICKET_RU_RULES", "Product Ticket Read Update Rule", 21, "ProductTicketRuRule"),
    TASK("TASK", "Task", 22, "Task"),
    TASK_TYPE("TASK_TYPE", "Task Type", 23, "TaskType"),
    NOTE("NOTE", "Note", 24, "Note"),
    ACTIVITY("ACTIVITY", "Activity", 25, "Activity"),
    CAMPAIGN("CAMPAIGN", "Campaign", 26, "Campaign"),
    PARTNER("PARTNER", "Partner", 27, "Partner"),
    PRODUCT_TEMPLATE_WALK_IN_FORMS(
            "PRODUCT_TEMPLATE_WALK_IN_FORMS", "Product Template Walk In Forms", 28, "ProductTemplateWalkInForm"),
    PRODUCT_WALK_IN_FORMS("PRODUCT_WALK_IN_FORMS", "Product Walk In Forms", 29, "ProductWalkInForms"),
    TICKET_DUPLICATION_RULES("TICKET_DUPLICATION_RULES", "Ticket Duplication Rules", 30, "TicketDuplicationRule"),
	TICKET_PE_DUPLICATION_RULES("TICKET_PE_DUPLICATION_RULES", "Ticket Pe Duplication Rules", 31, "TicketPeDuplicationRule");

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
            Map.entry(PRODUCT_WALK_IN_FORMS, "ProductWalkInForms"),
            Map.entry(TICKET_DUPLICATION_RULES, "TicketDuplicationRules"),
		    Map.entry(TICKET_PE_DUPLICATION_RULES, "TicketPeDuplicationRules"));

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
        return TableHolder.get(this);
    }

    public String getClassName() {
        return ClassHolder.get(this).getSimpleName();
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
            TABLE_MAP.put(TICKET, EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_TICKETS);
            TABLE_MAP.put(OWNER, EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_OWNERS);
            TABLE_MAP.put(PRODUCT, EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_PRODUCTS);
            TABLE_MAP.put(PRODUCT_TEMPLATE, EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_PRODUCT_TEMPLATES);
            TABLE_MAP.put(PRODUCT_COMM, EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_PRODUCT_COMMS);
            TABLE_MAP.put(STAGE, EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_STAGES);
            TABLE_MAP.put(
                    TICKET_C_USER_DISTRIBUTION,
                    EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_TICKET_C_USER_DISTRIBUTIONS);
            TABLE_MAP.put(
                    TICKET_RU_USER_DISTRIBUTION,
                    EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_TICKET_RU_USER_DISTRIBUTIONS);
            TABLE_MAP.put(
                    PRODUCT_TICKET_C_RULE, EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_PRODUCT_TICKET_C_RULES);
            TABLE_MAP.put(
                    PRODUCT_TICKET_RU_RULE, EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_PRODUCT_TICKET_RU_RULES);
            TABLE_MAP.put(TASK, EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_TASKS);
            TABLE_MAP.put(TASK_TYPE, EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_TASK_TYPES);
            TABLE_MAP.put(NOTE, EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_NOTES);
            TABLE_MAP.put(ACTIVITY, EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_ACTIVITIES);
            TABLE_MAP.put(CAMPAIGN, EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_CAMPAIGNS);
            TABLE_MAP.put(PARTNER, EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_PARTNERS);
            TABLE_MAP.put(
                    PRODUCT_TEMPLATE_WALK_IN_FORMS,
                    EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_PRODUCT_TEMPLATE_WALK_IN_FORMS);
            TABLE_MAP.put(
                    PRODUCT_WALK_IN_FORMS, EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_PRODUCT_WALK_IN_FORMS);
            TABLE_MAP.put(
                    TICKET_DUPLICATION_RULES,
                    EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_TICKET_DUPLICATION_RULES);
			TABLE_MAP.put(
					TICKET_PE_DUPLICATION_RULES,
					EntityProcessor.ENTITY_PROCESSOR.ENTITY_PROCESSOR_TICKET_PE_DUPLICATION_RULES);
        }

        static Table<?> get(EntitySeries series) {
            return TABLE_MAP.get(series);
        }
    }

    private static class ClassHolder {
        private static final Map<EntitySeries, Class<?>> CLASS_MAP = new EnumMap<>(EntitySeries.class);

        static {
            CLASS_MAP.put(TICKET, Ticket.class);
            CLASS_MAP.put(OWNER, Owner.class);
            CLASS_MAP.put(PRODUCT, Product.class);
            CLASS_MAP.put(PRODUCT_TEMPLATE, ProductTemplate.class);
            CLASS_MAP.put(PRODUCT_COMM, ProductComm.class);
            CLASS_MAP.put(STAGE, Stage.class);
            CLASS_MAP.put(TICKET_C_USER_DISTRIBUTION, TicketCUserDistribution.class);
            CLASS_MAP.put(TICKET_RU_USER_DISTRIBUTION, TicketRuUserDistribution.class);
            CLASS_MAP.put(PRODUCT_TICKET_C_RULE, ProductTicketCRule.class);
            CLASS_MAP.put(PRODUCT_TICKET_RU_RULE, ProductTicketRuRule.class);
            CLASS_MAP.put(TASK, Task.class);
            CLASS_MAP.put(TASK_TYPE, TaskType.class);
            CLASS_MAP.put(NOTE, Note.class);
            CLASS_MAP.put(ACTIVITY, Activity.class);
            CLASS_MAP.put(CAMPAIGN, Campaign.class);
            CLASS_MAP.put(PARTNER, Partner.class);
            CLASS_MAP.put(PRODUCT_TEMPLATE_WALK_IN_FORMS, ProductTemplateWalkInForm.class);
            CLASS_MAP.put(PRODUCT_WALK_IN_FORMS, ProductWalkInForm.class);
            CLASS_MAP.put(TICKET_DUPLICATION_RULES, TicketDuplicationRule.class);
			CLASS_MAP.put(TICKET_PE_DUPLICATION_RULES, TicketPeDuplicationRule.class);
        }

        static Class<?> get(EntitySeries series) {
            return CLASS_MAP.get(series);
        }
    }
}
