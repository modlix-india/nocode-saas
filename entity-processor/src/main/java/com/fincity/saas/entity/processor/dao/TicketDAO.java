package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorActivities.ENTITY_PROCESSOR_ACTIVITIES;
import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTasks.ENTITY_PROCESSOR_TASKS;
import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.HavingCondition;
import com.fincity.saas.entity.processor.dao.base.BaseProcessorDAO;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProducts;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import com.fincity.saas.entity.processor.model.common.Email;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.product.ProductTicketRuRuleService;
import com.fincity.saas.entity.processor.service.rule.TicketPeDuplicationRuleService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.SelectJoinStep;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Component
public class TicketDAO extends BaseProcessorDAO<EntityProcessorTicketsRecord, Ticket> {

    private static final String SUBQUERY_ALIAS = "activityTickets";
    private final Field<ULong> productIdField;

    private ActivityDAO activityDAO;
    private ProductTicketRuRuleService productTicketRuRuleService;
    private TicketPeDuplicationRuleService ticketPeDuplicationRuleService;

    protected TicketDAO() {
        super(
                Ticket.class,
                ENTITY_PROCESSOR_TICKETS,
                ENTITY_PROCESSOR_TICKETS.ID,
                ENTITY_PROCESSOR_TICKETS.ASSIGNED_USER_ID);
        this.productIdField = ENTITY_PROCESSOR_TICKETS.PRODUCT_ID;
    }

    @Lazy
    @Autowired
    public void setActivityDAO(ActivityDAO activityDAO) {
        this.activityDAO = activityDAO;
    }

    @Lazy
    @Autowired
    private void setProductTicketRuRuleService(ProductTicketRuRuleService productTicketRuRuleService) {
        this.productTicketRuRuleService = productTicketRuRuleService;
    }

    @Lazy
    @Autowired
    private void setTicketPeDuplicationRuleService(TicketPeDuplicationRuleService ticketPeDuplicationRuleService) {
        this.ticketPeDuplicationRuleService = ticketPeDuplicationRuleService;
    }

    public Flux<Ticket> getAllClientTicketsByDnc(ULong clientId, Boolean dnc) {
        return Flux.from(dslContext
                        .selectFrom(table)
                        .where(ENTITY_PROCESSOR_TICKETS.CLIENT_ID.eq(clientId))
                        .and(ENTITY_PROCESSOR_TICKETS.DNC.eq(dnc)))
                .map(rec -> rec.into(this.pojoClass));
    }

    public Flux<Ticket> getAllOwnerTickets(ULong ownerId) {
        return Flux.from(dslContext.selectFrom(table).where(ENTITY_PROCESSOR_TICKETS.OWNER_ID.eq(ownerId)))
                .map(rec -> rec.into(this.pojoClass));
    }

    public Mono<Ticket> readTicketByNumberAndEmail(
            AbstractCondition condition,
            ProcessorAccess access,
            ULong productId,
            PhoneNumber phoneNumber,
            Email email) {

        return FlatMapUtil.flatMapMono(
                () -> this.getOwnerIdentifierConditions(condition, access, productId, phoneNumber, email)
                        .map(ownerIdentifierConditions ->
                                super.addAppCodeAndClientCode(ownerIdentifierConditions, access)),
                super::filter,
                (pCondition, jCondition) -> Mono.from(this.dslContext
                                .selectFrom(this.table)
                                .where(jCondition.and(super.isActiveTrue()))
                                .orderBy(this.updatedByField.desc())
                                .limit(1))
                        .map(e -> e.into(this.pojoClass)));
    }

    public Mono<List<Ticket>> readTicketsByNumberAndEmail(
            AbstractCondition condition,
            ProcessorAccess access,
            ULong productId,
            PhoneNumber phoneNumber,
            Email email) {

        return FlatMapUtil.flatMapMono(
                () -> this.getOwnerIdentifierConditions(condition, access, productId, phoneNumber, email)
                        .map(ownerIdentifierConditions ->
                                super.addAppCodeAndClientCode(ownerIdentifierConditions, access)),
                super::filter,
                (pCondition, jCondition) -> Flux.from(
                                this.dslContext.selectFrom(this.table).where(jCondition.and(super.isActiveTrue())))
                        .map(e -> e.into(this.pojoClass))
                        .collectList());
    }

    private Mono<AbstractCondition> getOwnerIdentifierConditions(
            AbstractCondition condition,
            ProcessorAccess access,
            ULong productId,
            PhoneNumber phoneNumber,
            Email email) {

        return FlatMapUtil.flatMapMono(
                () -> this.ticketPeDuplicationRuleService.getTicketCondition(access, phoneNumber, email),
                pECondition -> {
                    List<AbstractCondition> conditions = new ArrayList<>();

                    if (condition != null && condition.isNonEmpty()) conditions.add(condition);

                    if (pECondition != null && pECondition.isNonEmpty()) conditions.add(pECondition);

                    if (productId != null) conditions.add(FilterCondition.make(Ticket.Fields.productId, productId));

                    if (conditions.isEmpty()) return Mono.empty();

                    return Mono.just(ComplexCondition.and(conditions));
                });
    }

    @Override
    public List<Field<?>> getMainTableBaseFields(List<String> tableFields, MultiValueMap<String, String> queryParams) {
        List<Field<?>> list = super.getMainTableBaseFields(tableFields, queryParams);

        if (tableFields == null || tableFields.isEmpty()) {
            list.add(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS.PRODUCT_TEMPLATE_ID);
            list.add(DSL.field(DSL.name("latest_task", "LATEST_TASK_DUE_DATE"), LocalDateTime.class));
            return list;
        }

        if (tableFields.contains(Ticket.Fields.productTemplateId))
            list.add(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS.PRODUCT_TEMPLATE_ID);

        if (tableFields.contains(Ticket.Fields.latestTaskDueDate))
            list.add(DSL.field(DSL.name("latest_task", "LATEST_TASK_DUE_DATE"), LocalDateTime.class));

        return list;
    }

    @Override
    public SelectJoinStep<Record> applyBaseTableJoins(
            SelectJoinStep<Record> query, MultiValueMap<String, String> queryParams) {

        Table<?> taskTable = this.getLatestTaskTable();

        return query.join(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS)
                .on(this.productIdField.eq(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS.ID))
                .leftJoin(taskTable)
                .on(this.idField.eq(taskTable.field(ENTITY_PROCESSOR_TASKS.TICKET_ID)));
    }

    @Override
    public SelectJoinStep<Record1<Integer>> applyCountBaseTableJoins(
            SelectJoinStep<Record1<Integer>> query, MultiValueMap<String, String> queryParams) {
        return super.applyCountBaseTableJoins(query, queryParams)
                .join(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS)
                .on(this.productIdField.eq(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS.ID));
    }

    @Override
    public Mono<
                    Tuple2<
                            Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>>,
                            Map<String, Tuple2<Table<?>, String>>>>
            getSelectJointStepEager(
                    List<String> tableFields,
                    MultiValueMap<String, String> queryParams,
                    Map<String, AbstractCondition> subQueryConditions) {

        if (subQueryConditions == null || subQueryConditions.isEmpty())
            return super.getSelectJointStepEager(tableFields, queryParams, null);

        AbstractCondition activityCondition = subQueryConditions.get(SUBQUERY_ALIAS);
        if (activityCondition == null || activityCondition.isEmpty())
            return super.getSelectJointStepEager(tableFields, queryParams, null);

        return this.buildActivitiesSubqueryTable(activityCondition)
                .flatMap(subqueryTable -> super.getSelectJointStepEager(tableFields, queryParams, null)
                        .map(tuple -> {
                            SelectJoinStep<Record> recordQuery = tuple.getT1().getT1();
                            SelectJoinStep<Record1<Integer>> countQuery =
                                    tuple.getT1().getT2();
                            recordQuery = recordQuery
                                    .join(subqueryTable)
                                    .on(this.idField.eq(subqueryTable.field(ENTITY_PROCESSOR_ACTIVITIES.TICKET_ID)));
                            countQuery = countQuery
                                    .join(subqueryTable)
                                    .on(this.idField.eq(subqueryTable.field(ENTITY_PROCESSOR_ACTIVITIES.TICKET_ID)));
                            return Tuples.of(Tuples.of(recordQuery, countQuery), tuple.getT2());
                        }));
    }

    @SuppressWarnings("unchecked")
    private Mono<Table<Record>> buildActivitiesSubqueryTable(AbstractCondition subQueryCondition) {

        SelectJoinStep<Record> baseQuery = (SelectJoinStep<Record>) (SelectJoinStep<?>)
                dslContext.select(ENTITY_PROCESSOR_ACTIVITIES.TICKET_ID).from(ENTITY_PROCESSOR_ACTIVITIES);

        boolean hasGroupCondition = subQueryCondition.hasGroupCondition();
        boolean isHavingCondition = subQueryCondition instanceof HavingCondition;

        AbstractCondition whereCondition;
        AbstractCondition havingCondition;

        if (hasGroupCondition) {
            whereCondition = subQueryCondition.getWhereCondition();
            havingCondition = subQueryCondition.getGroupCondition();
        } else if (isHavingCondition) {
            whereCondition = null;
            havingCondition = subQueryCondition;
        } else {
            whereCondition = subQueryCondition;
            havingCondition = null;
        }

        Mono<Condition> whereCondMono = whereCondition == null || !whereCondition.isNonEmpty()
                ? Mono.just(DSL.noCondition())
                : this.activityDAO.filter(whereCondition, baseQuery);

        Mono<Optional<Condition>> havingCondMono = havingCondition == null || !havingCondition.isNonEmpty()
                ? Mono.just(Optional.empty())
                : this.activityDAO.filterHaving(havingCondition, baseQuery).map(Optional::of);

        return Mono.zip(whereCondMono, havingCondMono).map(tuple -> {
            Condition whereCond = tuple.getT1();
            Optional<Condition> havingCondOpt = tuple.getT2();

            SelectConditionStep<Record> conditionStep = baseQuery.where(whereCond);

            return havingCondOpt
                    .map(condition -> conditionStep
                            .groupBy(ENTITY_PROCESSOR_ACTIVITIES.TICKET_ID)
                            .having(condition)
                            .asTable(SUBQUERY_ALIAS))
                    .orElseGet(() -> conditionStep
                            .groupBy(ENTITY_PROCESSOR_ACTIVITIES.TICKET_ID)
                            .asTable(SUBQUERY_ALIAS));
        });
    }

    @Override
    protected Mono<Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>>> getSelectJointStep() {

        Table<?> taskTable = this.getLatestTaskTable();

        return Mono.just(Tuples.of(
                dslContext
                        .select(Arrays.asList(table.fields()))
                        .select(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS.PRODUCT_TEMPLATE_ID)
                        .select(taskTable.field("latestTaskDueDate", LocalDateTime.class))
                        .from(table)
                        .join(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS)
                        .on(this.productIdField.eq(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS.ID))
                        .leftJoin(taskTable)
                        .on(this.idField.eq(taskTable.field(ENTITY_PROCESSOR_TASKS.TICKET_ID))),
                dslContext
                        .select(DSL.count())
                        .from(table)
                        .join(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS)
                        .on(this.productIdField.eq(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS.ID))));
    }

    @Override
    public Mono<AbstractCondition> processorAccessCondition(AbstractCondition condition, ProcessorAccess access) {
        return FlatMapUtil.flatMapMono(
                        () -> this.productTicketRuRuleService
                                .getUserReadConditions(access)
                                .map(rCondition ->
                                        condition != null ? ComplexCondition.and(condition, rCondition) : rCondition),
                        rCondition -> Mono.just(super.addAppCodeAndClientCode(rCondition, access)),
                        (rCondition, readCondition) -> super.processorAccessCondition(condition, access),
                        (rCondition, readCondition, baseCondition) -> readCondition == null
                                ? Mono.just(baseCondition)
                                : Mono.just(ComplexCondition.or(baseCondition, readCondition)))
                .switchIfEmpty(super.processorAccessCondition(condition, access));
    }

    private Table<?> getLatestTaskTable() {
        String taskAlias = "latest_task";

        return dslContext
                .select(
                        ENTITY_PROCESSOR_TASKS.TICKET_ID,
                        DSL.min(ENTITY_PROCESSOR_TASKS.DUE_DATE).as("LATEST_TASK_DUE_DATE"))
                .from(ENTITY_PROCESSOR_TASKS)
                .where(ENTITY_PROCESSOR_TASKS.DUE_DATE.ge(DSL.currentLocalDateTime()))
                .and(ENTITY_PROCESSOR_TASKS.IS_COMPLETED.eq(false))
                .groupBy(ENTITY_PROCESSOR_TASKS.TICKET_ID)
                .asTable(taskAlias);
    }
}
