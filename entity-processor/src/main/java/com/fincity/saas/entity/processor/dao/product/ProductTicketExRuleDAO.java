package com.fincity.saas.entity.processor.dao.product;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_PRODUCTS;
import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES;
import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_TICKETS;

import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.product.ProductTicketExRule;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorProductTicketExRulesRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.time.LocalDateTime;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ProductTicketExRuleDAO
        extends BaseUpdatableDAO<EntityProcessorProductTicketExRulesRecord, ProductTicketExRule> {

    protected ProductTicketExRuleDAO() {
        super(
                ProductTicketExRule.class,
                ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES,
                ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES.ID);
    }

    public Mono<ProductTicketExRule> findActiveRuleByProduct(
            ProcessorAccess access, ULong productId, String source) {

        return Mono.from(this.dslContext
                        .selectFrom(this.table)
                        .where(ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES.APP_CODE.eq(access.getAppCode()))
                        .and(ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES.CLIENT_CODE.eq(access.getEffectiveClientCode()))
                        .and(ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES.PRODUCT_ID.eq(productId))
                        .and(ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES.SOURCE.eq(source))
                        .and(ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES.IS_ACTIVE.eq(Boolean.TRUE)))
                .map(e -> e.into(this.pojoClass));
    }

    public Mono<ProductTicketExRule> findActiveRuleByTemplate(
            ProcessorAccess access, ULong productTemplateId, String source) {

        return Mono.from(this.dslContext
                        .selectFrom(this.table)
                        .where(ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES.APP_CODE.eq(access.getAppCode()))
                        .and(ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES.CLIENT_CODE.eq(access.getEffectiveClientCode()))
                        .and(ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES.PRODUCT_TEMPLATE_ID.eq(productTemplateId))
                        .and(ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES.SOURCE.eq(source))
                        .and(ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES.IS_ACTIVE.eq(Boolean.TRUE)))
                .map(e -> e.into(this.pojoClass));
    }

    public Mono<Integer> recalculateExpiresOn(ProductTicketExRule rule) {

        var ticketsTable = ENTITY_PROCESSOR_TICKETS;
        var rulesTable = ENTITY_PROCESSOR_PRODUCT_TICKET_EX_RULES;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime newExpiresOn = now.plusDays(rule.getExpiryDays());

        Condition baseCondition = ticketsTable
                .APP_CODE
                .eq(rule.getAppCode())
                .and(ticketsTable.CLIENT_CODE.eq(rule.getClientCode()))
                .and(ticketsTable.SOURCE.eq(rule.getSource()))
                .and(ticketsTable.EXPIRES_ON.isNull().or(ticketsTable.EXPIRES_ON.gt(now)));

        if (rule.getProductId() != null) {
            // Product-level rule: directly match tickets by product ID
            return Mono.from(this.dslContext
                    .update(ticketsTable)
                    .set(ticketsTable.EXPIRES_ON, newExpiresOn)
                    .where(baseCondition)
                    .and(ticketsTable.PRODUCT_ID.eq(rule.getProductId())));
        }

        // Template-level rule: join with products table to find tickets whose product
        // belongs to this template, but EXCLUDE tickets that have their own product-level
        // expiration rule (product rules take priority over template rules)
        var productsTable = ENTITY_PROCESSOR_PRODUCTS;

        return Mono.from(this.dslContext
                .update(ticketsTable)
                .set(ticketsTable.EXPIRES_ON, newExpiresOn)
                .where(baseCondition)
                .and(ticketsTable.PRODUCT_ID.in(
                        DSL.select(productsTable.ID)
                                .from(productsTable)
                                .where(productsTable.PRODUCT_TEMPLATE_ID.eq(rule.getProductTemplateId()))
                                .and(productsTable.APP_CODE.eq(rule.getAppCode()))
                                .and(productsTable.CLIENT_CODE.eq(rule.getClientCode()))))
                .andNot(DSL.exists(
                        DSL.selectOne()
                                .from(rulesTable)
                                .where(rulesTable.PRODUCT_ID.eq(ticketsTable.PRODUCT_ID))
                                .and(rulesTable.SOURCE.eq(rule.getSource()))
                                .and(rulesTable.APP_CODE.eq(rule.getAppCode()))
                                .and(rulesTable.CLIENT_CODE.eq(rule.getClientCode()))
                                .and(rulesTable.IS_ACTIVE.eq(Boolean.TRUE)))));
    }
}
