package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorActivities.ENTITY_PROCESSOR_ACTIVITIES;
import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS;

import com.fincity.saas.entity.processor.dto.rule.ProductTicketExRule;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProducts;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class TicketExpirationDAO {

    private static final int BATCH_LIMIT = 5000;

    private final DSLContext dslContext;

    public TicketExpirationDAO(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    public Mono<List<ULong>> expireTicketsForRule(
            ProductTicketExRule rule, LocalDateTime cutoff, LocalDateTime expiredOn, Set<ULong> excludeIds) {

        if (cutoff == null
                || expiredOn == null
                || rule.getSource() == null
                || rule.getSource().isBlank()) return Mono.just(List.of());

        boolean byTemplate = rule.getProductTemplateId() != null;

        Table<?> lastActTable = dslContext
                .select(
                        ENTITY_PROCESSOR_ACTIVITIES.TICKET_ID,
                        DSL.max(ENTITY_PROCESSOR_ACTIVITIES.ACTIVITY_DATE).as("LAST_ACT"))
                .from(ENTITY_PROCESSOR_ACTIVITIES)
                .groupBy(ENTITY_PROCESSOR_ACTIVITIES.TICKET_ID)
                .asTable("last_act");

        Field<LocalDateTime> lastActField = lastActTable.field("LAST_ACT", SQLDataType.LOCALDATETIME(0));
        Field<ULong> lastActTicketId = lastActTable.field("TICKET_ID", SQLDataType.BIGINTUNSIGNED);

        var baseSelect = dslContext
                .select(ENTITY_PROCESSOR_TICKETS.ID)
                .from(ENTITY_PROCESSOR_TICKETS)
                .leftJoin(lastActTable)
                .on(ENTITY_PROCESSOR_TICKETS.ID.eq(lastActTicketId));

        var joinStep = byTemplate
                ? baseSelect
                        .join(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS)
                        .on(ENTITY_PROCESSOR_TICKETS.PRODUCT_ID.eq(
                                EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS.ID))
                        .and(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS.PRODUCT_TEMPLATE_ID.eq(
                                rule.getProductTemplateId()))
                : baseSelect;

        Condition condition = ENTITY_PROCESSOR_TICKETS
                .APP_CODE
                .eq(rule.getAppCode())
                .and(ENTITY_PROCESSOR_TICKETS.CLIENT_CODE.eq(rule.getClientCode()))
                .and(ENTITY_PROCESSOR_TICKETS.SOURCE.eq(rule.getSource()))
                .and(ENTITY_PROCESSOR_TICKETS.IS_EXPIRED.ne(true))
                .and(DSL.coalesce(lastActField, ENTITY_PROCESSOR_TICKETS.CREATED_AT)
                        .lt(cutoff));

        if (!byTemplate) condition = condition.and(ENTITY_PROCESSOR_TICKETS.PRODUCT_ID.eq(rule.getProductId()));

        if (excludeIds != null && !excludeIds.isEmpty())
            condition = condition.and(ENTITY_PROCESSOR_TICKETS.ID.notIn(excludeIds));

        var subquery = joinStep.where(condition).limit(BATCH_LIMIT);

        return Mono.fromCallable(() -> {
                    var candidateIds = dslContext.fetch(subquery).getValues(ENTITY_PROCESSOR_TICKETS.ID);

                    if (candidateIds.isEmpty()) return List.<ULong>of();

                    dslContext
                            .update(ENTITY_PROCESSOR_TICKETS)
                            .set(ENTITY_PROCESSOR_TICKETS.IS_EXPIRED, true)
                            .set(ENTITY_PROCESSOR_TICKETS.EXPIRED_ON, expiredOn)
                            .where(ENTITY_PROCESSOR_TICKETS.ID.in(candidateIds))
                            .and(ENTITY_PROCESSOR_TICKETS.APP_CODE.eq(rule.getAppCode()))
                            .and(ENTITY_PROCESSOR_TICKETS.CLIENT_CODE.eq(rule.getClientCode()))
                            .and(ENTITY_PROCESSOR_TICKETS.IS_EXPIRED.ne(true))
                            .execute();

                    return candidateIds;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Integer> expireTicketsForRuleFully(
            ProductTicketExRule rule, LocalDateTime cutoff, LocalDateTime expiredOn, Set<ULong> excludeIds) {

        return this.expireTicketsForRule(rule, cutoff, expiredOn, excludeIds)
                .expand(batch -> {
                    excludeIds.addAll(batch);
                    if (batch.size() < BATCH_LIMIT) return Mono.empty();
                    return this.expireTicketsForRule(rule, cutoff, expiredOn, excludeIds);
                })
                .map(Collection::size)
                .reduce(0, Integer::sum);
    }
}
