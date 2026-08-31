package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorActivities.ENTITY_PROCESSOR_ACTIVITIES;
import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTasks.ENTITY_PROCESSOR_TASKS;
import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.condition.HavingCondition;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.entity.processor.dao.base.BaseProcessorDAO;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProducts;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import com.fincity.saas.entity.processor.model.common.Email;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.response.WhatsappConversationResponse;
import com.fincity.saas.entity.processor.service.product.ProductTicketRuRuleService;
import com.fincity.saas.entity.processor.service.rule.TicketPeDuplicationRuleService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import com.fincity.saas.entity.processor.oserver.files.model.FileDetail;
import com.fincity.saas.commons.util.StringUtil;
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

    private static final String LATEST_TASK_DUE_DATE = "latestTaskDueDate";

    protected TicketDAO() {
        super(
                Ticket.class,
                ENTITY_PROCESSOR_TICKETS,
                ENTITY_PROCESSOR_TICKETS.ID,
                ENTITY_PROCESSOR_TICKETS.ASSIGNED_USER_ID);
        this.productIdField = ENTITY_PROCESSOR_TICKETS.PRODUCT_ID;
    }

    @Override
    public Field getField(String fieldName, SelectJoinStep<Record> selectJoinStep) {
        if (LATEST_TASK_DUE_DATE.equals(fieldName)) {
            // Correlated subquery for sorting/filtering by nearest task due date.
            // MIN(future incomplete due dates), falling back to MAX(any incomplete due date).
            // No .as() alias — JOOQ renders aliased fields as just the alias name in ORDER BY,
            // which fails because this expression is not in the SELECT clause.
            return DSL.coalesce(
                    DSL.field(DSL.select(DSL.min(ENTITY_PROCESSOR_TASKS.DUE_DATE))
                            .from(ENTITY_PROCESSOR_TASKS)
                            .where(ENTITY_PROCESSOR_TASKS.TICKET_ID.eq(ENTITY_PROCESSOR_TICKETS.ID))
                            .and(ENTITY_PROCESSOR_TASKS.IS_COMPLETED.eq(DSL.inline(false)))
                            .and(ENTITY_PROCESSOR_TASKS.DUE_DATE.ge(DSL.currentLocalDateTime()))),
                    DSL.field(DSL.select(DSL.max(ENTITY_PROCESSOR_TASKS.DUE_DATE))
                            .from(ENTITY_PROCESSOR_TASKS)
                            .where(ENTITY_PROCESSOR_TASKS.TICKET_ID.eq(ENTITY_PROCESSOR_TICKETS.ID))
                            .and(ENTITY_PROCESSOR_TASKS.IS_COMPLETED.eq(DSL.inline(false))))
            );
        }
        return super.getField(fieldName, selectJoinStep);
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

    public Mono<Integer> updateDncByClientId(ULong clientId, Boolean dnc) {
        return Mono.from(
                this.dslContext.update(ENTITY_PROCESSOR_TICKETS)
                        .set(ENTITY_PROCESSOR_TICKETS.DNC, dnc)
                        .set(ENTITY_PROCESSOR_TICKETS.UPDATED_AT, ENTITY_PROCESSOR_TICKETS.UPDATED_AT)
                        .where(ENTITY_PROCESSOR_TICKETS.CLIENT_ID.eq(clientId))
                        .and(ENTITY_PROCESSOR_TICKETS.DNC.ne(dnc)));
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

    /**
     * Every active deal a WhatsApp message on this number belongs to, most recently updated first.
     *
     * <p>Deliberately a plain phone match rather than reusing {@code readTicketByNumberAndEmail},
     * whose matching is driven by the per-app duplicate-detection rule. That rule can require an
     * email, which an inbound message never carries, so it is the wrong instrument here.
     *
     * <p>Both sides store E164 with the leading {@code +} ({@code PhoneUtil.parse} on this side,
     * {@code PhoneNumber.ofWhatsapp} on the message side), so this compares like with like.
     *
     * <p>Returns all matches rather than one because a customer can hold several deals on the same
     * product, and the thread is shared across them: they all move to the top of the inbox together
     * when a message arrives. The caller stamps the message's {@code TICKET_ID} with the first.
     *
     * <p>{@code productIds} narrows to the products the business number serves. A number can serve
     * several, which is why this takes a list rather than one id: narrowing to a single product would
     * hide a customer's existing deal on a sibling product and manufacture a duplicate for them.
     * Empty or null means the number serves everything, so the match is on the customer's number
     * alone.
     */
    public Mono<List<Ticket>> readActiveByProductAndPhone(
            ProcessorAccess access, List<ULong> productIds, PhoneNumber phoneNumber) {

        if (phoneNumber == null || phoneNumber.getNumber() == null || phoneNumber.getNumber().isBlank())
            return Mono.just(List.of());

        List<AbstractCondition> conditions = new ArrayList<>();
        conditions.add(onEitherNumber(phoneNumber.getNumber()));

        if (productIds != null && !productIds.isEmpty())
            conditions.add(new FilterCondition()
                    .setField(Ticket.Fields.productId)
                    .setOperator(FilterConditionOperator.IN)
                    .setMultiValue(List.copyOf(productIds)));

        AbstractCondition condition = super.addAppCodeAndClientCode(ComplexCondition.and(conditions), access);

        return FlatMapUtil.flatMapMono(
                () -> super.filter(condition),
                jCondition -> Flux.from(this.dslContext
                                .selectFrom(this.table)
                                .where(jCondition.and(super.isActiveTrue()))
                                .orderBy(ENTITY_PROCESSOR_TICKETS.UPDATED_AT.desc()))
                        .map(e -> e.into(this.pojoClass))
                        .collectList());
    }

    /**
     * Matches a customer's number against either number a deal can be reached on.
     *
     * <p>The reason every WhatsApp lookup has to search both columns rather than just
     * {@code PHONE_NUMBER}. Once someone records a separate WhatsApp number on a deal, the customer's
     * replies arrive from that number, and a phone-only match finds nothing. On the inbound path that
     * is not a missing row but a wrong one: the message resolves to no deal, so it creates a fresh one
     * for a customer who already has a deal - the exact duplicate the second column exists to avoid,
     * produced by the act of recording the number correctly.
     *
     * <p>An OR rather than a coalesce so both columns stay indexable;
     * {@code IDX10_TICKETS_APP_CLIENT_WHATSAPP} covers the second half.
     */
    private static AbstractCondition onEitherNumber(String number) {
        return ComplexCondition.or(
                FilterCondition.make(Ticket.Fields.phoneNumber, number),
                FilterCondition.make(Ticket.Fields.whatsappNumber, number));
    }

    /**
     * Moves deals to the top of the conversation list.
     *
     * <p>Deliberately a direct {@code UPDATE} of {@code LAST_MESSAGE_AT} alone, not a read-modify-
     * write through the DTO. A message arrives with no user attached, so going through the normal
     * update path would stamp {@code UPDATED_BY} null and {@code UPDATED_AT} now, turning every
     * inbound message into a phantom edit in the deal's audit trail.
     */
    public Mono<Integer> touchLastMessageAt(List<ULong> ticketIds, LocalDateTime occurredAt) {

        if (ticketIds == null || ticketIds.isEmpty()) return Mono.just(0);

        return Mono.from(this.dslContext
                .update(ENTITY_PROCESSOR_TICKETS)
                .set(ENTITY_PROCESSOR_TICKETS.LAST_MESSAGE_AT, occurredAt)
                .where(ENTITY_PROCESSOR_TICKETS.ID.in(ticketIds))
                // A late webhook must not drag the conversation backwards past a newer message.
                .and(ENTITY_PROCESSOR_TICKETS
                        .LAST_MESSAGE_AT
                        .isNull()
                        .or(ENTITY_PROCESSOR_TICKETS.LAST_MESSAGE_AT.lt(occurredAt))));
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
            return list;
        }

        if (tableFields.contains(Ticket.Fields.productTemplateId))
            list.add(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS.PRODUCT_TEMPLATE_ID);

        return list;
    }

    @Override
    public SelectJoinStep<Record> applyBaseTableJoins(
            SelectJoinStep<Record> query, MultiValueMap<String, String> queryParams) {

        return query.join(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS)
                .on(this.productIdField.eq(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS.ID));
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

    public Flux<Ticket> readAllForBulkOp(
            AbstractCondition condition,
            String timezone,
            Map<String, AbstractCondition> subQueryConditions) {

        SelectJoinStep<Record> baseQuery = (SelectJoinStep<Record>) (SelectJoinStep<?>)
                dslContext.select(Arrays.asList(table.fields()))
                        .from(table)
                        .join(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS)
                        .on(this.productIdField.eq(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS.ID));

        Mono<SelectJoinStep<Record>> queryMono;

        if (subQueryConditions != null && !subQueryConditions.isEmpty()) {
            AbstractCondition activityCondition = subQueryConditions.get(SUBQUERY_ALIAS);
            if (activityCondition != null && !activityCondition.isEmpty()) {
                queryMono = this.buildActivitiesSubqueryTable(activityCondition)
                        .map(subqueryTable -> (SelectJoinStep<Record>) baseQuery
                                .join(subqueryTable)
                                .on(this.idField.eq(subqueryTable.field(ENTITY_PROCESSOR_ACTIVITIES.TICKET_ID))));
            } else {
                queryMono = Mono.just(baseQuery);
            }
        } else {
            queryMono = Mono.just(baseQuery);
        }

        return queryMono.flatMapMany(query -> this.filter(condition, query, timezone)
                .flatMapMany(jCondition -> Flux.from(query.where(jCondition.and(this.isActiveTrue())))
                        .map(rec -> rec.into(this.pojoClass))));
    }

    @SuppressWarnings("unchecked")
    public Mono<List<ULong>> readDistinctAssignedUserIds(
            AbstractCondition condition,
            String timezone,
            Map<String, AbstractCondition> subQueryConditions) {

        SelectJoinStep<Record> baseQuery = (SelectJoinStep<Record>) (SelectJoinStep<?>)
                dslContext.selectDistinct(ENTITY_PROCESSOR_TICKETS.ASSIGNED_USER_ID)
                        .from(table)
                        .join(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS)
                        .on(this.productIdField.eq(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS.ID));

        Mono<SelectJoinStep<Record>> queryMono;

        if (subQueryConditions != null && !subQueryConditions.isEmpty()) {
            AbstractCondition activityCondition = subQueryConditions.get(SUBQUERY_ALIAS);
            if (activityCondition != null && !activityCondition.isEmpty()) {
                queryMono = this.buildActivitiesSubqueryTable(activityCondition)
                        .map(subqueryTable -> (SelectJoinStep<Record>) baseQuery
                                .join(subqueryTable)
                                .on(this.idField.eq(subqueryTable.field(ENTITY_PROCESSOR_ACTIVITIES.TICKET_ID))));
            } else {
                queryMono = Mono.just(baseQuery);
            }
        } else {
            queryMono = Mono.just(baseQuery);
        }

        return queryMono.flatMap(query ->
                this.filter(condition, query, timezone)
                        .flatMap(jCondition -> Flux.from(query.where(jCondition.and(this.isActiveTrue())))
                                .map(rec -> rec.get(ENTITY_PROCESSOR_TICKETS.ASSIGNED_USER_ID))
                                .filter(Objects::nonNull)
                                .collectList()));
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

        return Mono.just(Tuples.of(
                dslContext
                        .select(Arrays.asList(table.fields()))
                        .select(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS.PRODUCT_TEMPLATE_ID)
                        .from(table)
                        .join(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS)
                        .on(this.productIdField.eq(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS.ID)),
                dslContext
                        .select(DSL.count())
                        .from(table)
                        .join(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS)
                        .on(this.productIdField.eq(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS.ID))));
    }

    /**
     * Active "deal" count for token metering: tickets of {@code (appCode, clientCode = M)}
     * still active. Keyed purely by app + client code, per the billing model.
     */
    public Mono<Long> countActiveTickets(String appCode, String clientCode) {
        return Mono.from(this.dslContext
                .select(DSL.count())
                .from(ENTITY_PROCESSOR_TICKETS)
                .where(ENTITY_PROCESSOR_TICKETS.APP_CODE.eq(appCode))
                .and(ENTITY_PROCESSOR_TICKETS.CLIENT_CODE.eq(clientCode))
                .and(ENTITY_PROCESSOR_TICKETS.IS_ACTIVE.eq(true)))
                .map(r -> r.value1().longValue());
    }

    @Override
    public Mono<AbstractCondition> processorAccessCondition(AbstractCondition condition, ProcessorAccess access) {
        if (access.getUser() == null && access.getUserInherit() == null)
            return Mono.just(super.addAppCodeAndClientCode(condition, access));

        AbstractCondition rawAccess = this.buildRawUserClientAccess(access);

        return this.productTicketRuRuleService
                .getUserReadConditions(access)
                .map(ruleCondition -> (AbstractCondition) ComplexCondition.or(rawAccess, ruleCondition))
                .defaultIfEmpty(rawAccess)
                .map(combinedAccess -> {
                    AbstractCondition full = condition != null && !condition.isEmpty()
                            ? ComplexCondition.and(condition, combinedAccess)
                            : combinedAccess;
                    return super.addAppCodeAndClientCode(full, access);
                });
    }

    private AbstractCondition buildRawUserClientAccess(ProcessorAccess access) {

        String userField = access.isOutsideUser() ? AbstractDTO.Fields.createdBy : this.jUserAccessField;
        List<ULong> subOrg = access.getUserInherit().getSubOrg();

        AbstractCondition userCondition = new FilterCondition()
                .setField(userField)
                .setOperator(FilterConditionOperator.IN)
                .setMultiValue(subOrg);

        if (access.isOutsideUser()) {
            return ComplexCondition.and(
                    FilterCondition.make(BaseProcessorDto.Fields.clientId, access.getUser().getClientId()),
                    userCondition);
        }

        if (!access.isHasBpAccess()) return userCondition;

        AbstractCondition clientCondition = new FilterCondition()
                .setField(BaseProcessorDto.Fields.clientId)
                .setOperator(FilterConditionOperator.IN)
                .setMultiValue(access.getUserInherit().getManagingClientIds());

        return ComplexCondition.or(clientCondition, userCondition);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<Page<Ticket>> readPageFilterWithTimezone(
            Pageable pageable, AbstractCondition condition, String timezone) {

        return super.readPageFilterWithTimezone(pageable, condition, timezone)
                .flatMap(this::enrichTicketsPage);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<Page<Map<String, Object>>> readPageFilterEagerWithTimezone(
            Pageable pageable,
            AbstractCondition condition,
            List<String> fields,
            String timezone,
            MultiValueMap<String, String> queryParams,
            Map<String, AbstractCondition> subQueryConditions) {

        return super.readPageFilterEagerWithTimezone(pageable, condition, fields, timezone, queryParams,
                subQueryConditions)
                .flatMap(this::enrichTicketMapsPage);
    }

    private Mono<Page<Ticket>> enrichTicketsPage(Page<Ticket> page) {

        List<Ticket> tickets = page.getContent();
        if (tickets.isEmpty()) return Mono.just(page);

        List<ULong> ticketIds = tickets.stream()
                .map(Ticket::getId)
                .toList();

        return Mono.zip(fetchLatestComments(ticketIds), fetchLatestTaskDueDates(ticketIds))
                .map(tuple -> {
                    Map<ULong, String> comments = tuple.getT1();
                    Map<ULong, LocalDateTime> dueDates = tuple.getT2();

                    tickets.forEach(ticket -> {
                        ticket.setLatestComment(comments.get(ticket.getId()));
                        ticket.setLatestTaskDueDate(dueDates.get(ticket.getId()));
                    });

                    return page;
                });
    }

    private Mono<Page<Map<String, Object>>> enrichTicketMapsPage(Page<Map<String, Object>> page) {

        List<Map<String, Object>> records = page.getContent();
        if (records.isEmpty()) return Mono.just(page);

        List<ULong> ticketIds = records.stream()
                .map(rec -> (ULong) rec.get("id"))
                .filter(Objects::nonNull)
                .toList();

        if (ticketIds.isEmpty()) return Mono.just(page);

        return Mono.zip(fetchLatestComments(ticketIds), fetchLatestTaskDueDates(ticketIds))
                .map(tuple -> {
                    Map<ULong, String> comments = tuple.getT1();
                    Map<ULong, LocalDateTime> dueDates = tuple.getT2();

                    records.forEach(rec -> {
                        ULong id = (ULong) rec.get("id");
                        if (id != null) {
                            rec.put("latestComment", comments.get(id));
                            rec.put(LATEST_TASK_DUE_DATE, dueDates.get(id));
                        }
                    });

                    return page;
                });
    }

    private Mono<Map<ULong, String>> fetchLatestComments(List<ULong> ticketIds) {

        Field<Integer> rowNum = DSL.rowNumber()
                .over(DSL.partitionBy(ENTITY_PROCESSOR_ACTIVITIES.TICKET_ID)
                        .orderBy(ENTITY_PROCESSOR_ACTIVITIES.ACTIVITY_DATE.desc()))
                .as("rn");

        Table<?> sub = dslContext
                .select(ENTITY_PROCESSOR_ACTIVITIES.TICKET_ID, ENTITY_PROCESSOR_ACTIVITIES.COMMENT, rowNum)
                .from(ENTITY_PROCESSOR_ACTIVITIES)
                .where(ENTITY_PROCESSOR_ACTIVITIES.TICKET_ID.in(ticketIds))
                .and(ENTITY_PROCESSOR_ACTIVITIES.COMMENT.isNotNull())
                .asTable("act_comment_sub");

        return Flux.from(dslContext
                        .select(sub.field(ENTITY_PROCESSOR_ACTIVITIES.TICKET_ID),
                                sub.field(ENTITY_PROCESSOR_ACTIVITIES.COMMENT))
                        .from(sub)
                        .where(sub.field("rn", Integer.class).eq(1)))
                .collectMap(
                        rec -> rec.get(sub.field(ENTITY_PROCESSOR_ACTIVITIES.TICKET_ID)),
                        rec -> rec.get(sub.field(ENTITY_PROCESSOR_ACTIVITIES.COMMENT)));
    }

    private Mono<Map<ULong, LocalDateTime>> fetchLatestTaskDueDates(List<ULong> ticketIds) {

        Field<Integer> rowNum = DSL.rowNumber()
                .over(DSL.partitionBy(ENTITY_PROCESSOR_TASKS.TICKET_ID)
                        .orderBy(
                                DSL.field(ENTITY_PROCESSOR_TASKS.DUE_DATE.ge(DSL.currentLocalDateTime()))
                                        .desc(),
                                DSL.if_(
                                                ENTITY_PROCESSOR_TASKS.DUE_DATE.ge(DSL.currentLocalDateTime()),
                                                ENTITY_PROCESSOR_TASKS.DUE_DATE,
                                                DSL.inline((LocalDateTime) null))
                                        .asc(),
                                ENTITY_PROCESSOR_TASKS.DUE_DATE.desc()))
                .as("rn");

        Table<?> sub = dslContext
                .select(ENTITY_PROCESSOR_TASKS.TICKET_ID, ENTITY_PROCESSOR_TASKS.DUE_DATE, rowNum)
                .from(ENTITY_PROCESSOR_TASKS)
                .where(ENTITY_PROCESSOR_TASKS.TICKET_ID.in(ticketIds))
                .and(ENTITY_PROCESSOR_TASKS.IS_COMPLETED.eq(DSL.inline(false)))
                .asTable("task_due_sub");

        return Flux.from(dslContext
                        .select(sub.field(ENTITY_PROCESSOR_TASKS.TICKET_ID),
                                sub.field(ENTITY_PROCESSOR_TASKS.DUE_DATE))
                        .from(sub)
                        .where(sub.field("rn", Integer.class).eq(1)))
                .collectMap(
                        rec -> rec.get(sub.field(ENTITY_PROCESSOR_TASKS.TICKET_ID)),
                        rec -> rec.get(sub.field(ENTITY_PROCESSOR_TASKS.DUE_DATE)));
    }

    // -------------------------------------------------------------------------------------------
    // WhatsApp inbox
    // -------------------------------------------------------------------------------------------

    private static final String CONVERSATION_ORDERED_AT = "orderedAt";
    private static final String CONVERSATION_LAST_MESSAGE_AT = "lastMessageAt";

    /**
     * The WhatsApp inbox: one row per customer number, over the deals the caller can see.
     *
     * <p>Runs on {@link #processorAccessCondition}, the same rule the Deals screen uses, so there is
     * no second definition of who can see a conversation. Grouped by number rather than by deal
     * because the customer holds a single thread on their handset regardless of how many deals we
     * have against them.
     *
     * <p>Lists every accessible deal, not only those with messages. A deal with no conversation
     * sorts on its {@code UPDATED_AT}, so the inbox doubles as an address book to start one from,
     * and a tenant that has just switched WhatsApp on sees a useful list rather than an empty one.
     */
    public Mono<Page<WhatsappConversationResponse>> readConversations(
            ProcessorAccess access, ULong productId, String search, Pageable pageable) {

        return FlatMapUtil.flatMapMono(
                () -> this.processorAccessCondition(conversationFilter(productId, search), access),
                super::filter,
                (condition, jCondition) -> {
                    Condition where = jCondition
                            .and(super.isActiveTrue())
                            .and(ENTITY_PROCESSOR_TICKETS.PHONE_NUMBER.isNotNull())
                            .and(ENTITY_PROCESSOR_TICKETS.PHONE_NUMBER.ne(DSL.inline("")));

                    return Mono.zip(countConversations(where), pageConversationKeys(where, pageable))
                            .flatMap(tuple -> attachDeals(where, tuple.getT2())
                                    .map(rows -> (Page<WhatsappConversationResponse>)
                                            new PageImpl<>(rows, pageable, tuple.getT1())));
                });
    }

    /**
     * The deals on a customer's number that this caller may see.
     *
     * <p>This is the gate for a conversation thread, and the reason the thread is a union rather
     * than a single ticket. A customer's history can span several deals, and a business number
     * change splits it further, so reading one ticket would show a fragment. Resolving the visible
     * set first and reading every message filed against it keeps the thread whole without ever
     * widening past what {@link #processorAccessCondition} allows.
     *
     * <p>Runs on the same condition the Deals screen uses, so conversation visibility cannot drift
     * from deal visibility.
     */
    public Mono<List<ULong>> readAccessibleTicketIdsByPhone(
            ProcessorAccess access, String phoneNumber, ULong productId) {
        return readAccessibleTicketIds(access, FilterCondition.make(Ticket.Fields.phoneNumber, phoneNumber), phoneNumber, productId);
    }

    /**
     * The same gate, for a WhatsApp thread rather than a call history.
     *
     * <p>Split from the phone version rather than widening it, because the two channels do not share
     * a number any more. A call goes to {@code PHONE_NUMBER} and its history groups on that alone; a
     * thread runs on whichever number the deal is messaged on, so it has to match either column or a
     * deal with a corrected WhatsApp number would show an empty conversation next to the messages it
     * actually holds.
     *
     * <p>Widening the shared method instead would have quietly pulled deals into call histories on
     * the strength of a number nobody ever dialled.
     */
    public Mono<List<ULong>> readAccessibleTicketIdsByWhatsappNumber(
            ProcessorAccess access, String number, ULong productId) {
        return readAccessibleTicketIds(access, onEitherNumber(number), number, productId);
    }

    private Mono<List<ULong>> readAccessibleTicketIds(
            ProcessorAccess access, AbstractCondition numberCondition, String number, ULong productId) {

        if (number == null || number.isBlank()) return Mono.just(List.of());

        List<AbstractCondition> conditions = new ArrayList<>();
        conditions.add(numberCondition);
        if (productId != null) conditions.add(FilterCondition.make(Ticket.Fields.productId, productId));

        return FlatMapUtil.flatMapMono(
                () -> this.processorAccessCondition(ComplexCondition.and(conditions), access),
                super::filter,
                (condition, jCondition) -> Flux.from(this.dslContext
                                .select(ENTITY_PROCESSOR_TICKETS.ID)
                                .from(this.table)
                                .where(jCondition.and(super.isActiveTrue())))
                        .map(Record1::value1)
                        .collectList());
    }

    private AbstractCondition conversationFilter(ULong productId, String search) {

        List<AbstractCondition> conditions = new ArrayList<>();

        if (productId != null) conditions.add(FilterCondition.make(Ticket.Fields.productId, productId));

        if (search != null && !search.isBlank())
            conditions.add(ComplexCondition.or(
                    FilterCondition.make(BaseUpdatableDto.Fields.name, search)
                            .setOperator(FilterConditionOperator.STRING_LOOSE_EQUAL),
                    FilterCondition.make(Ticket.Fields.phoneNumber, search)
                            .setOperator(FilterConditionOperator.STRING_LOOSE_EQUAL),
                    // The number an agent has in front of them is the one the customer messaged
                    // from, so searching the inbox for it has to find the deal even when that is the
                    // WhatsApp number rather than the number on file.
                    FilterCondition.make(Ticket.Fields.whatsappNumber, search)
                            .setOperator(FilterConditionOperator.STRING_LOOSE_EQUAL)));

        if (conditions.isEmpty()) return null;

        return conditions.size() == 1 ? conditions.getFirst() : ComplexCondition.and(conditions);
    }

    private Mono<Long> countConversations(Condition where) {
        return Mono.from(this.dslContext
                        .select(DSL.countDistinct(
                                ENTITY_PROCESSOR_TICKETS.DIAL_CODE, ENTITY_PROCESSOR_TICKETS.PHONE_NUMBER))
                        .from(this.table)
                        .where(where))
                .map(Record1::value1)
                .map(Integer::longValue)
                .defaultIfEmpty(0L);
    }

    /**
     * The page of customer numbers, with their sort keys. Deliberately separate from loading the
     * deals: aggregating and paging in SQL then hydrating only the page's numbers keeps the second
     * query bounded, and avoids GROUP_CONCAT, whose default length cap would silently truncate a
     * number held by many deals.
     */
    private Mono<List<WhatsappConversationResponse>> pageConversationKeys(Condition where, Pageable pageable) {

        Field<LocalDateTime> orderedAt = DSL.max(
                        DSL.coalesce(ENTITY_PROCESSOR_TICKETS.LAST_MESSAGE_AT, ENTITY_PROCESSOR_TICKETS.UPDATED_AT))
                .as(CONVERSATION_ORDERED_AT);
        Field<LocalDateTime> lastMessageAt =
                DSL.max(ENTITY_PROCESSOR_TICKETS.LAST_MESSAGE_AT).as(CONVERSATION_LAST_MESSAGE_AT);

        return Flux.from(this.dslContext
                        .select(
                                ENTITY_PROCESSOR_TICKETS.DIAL_CODE,
                                ENTITY_PROCESSOR_TICKETS.PHONE_NUMBER,
                                orderedAt,
                                lastMessageAt)
                        .from(this.table)
                        .where(where)
                        .groupBy(ENTITY_PROCESSOR_TICKETS.DIAL_CODE, ENTITY_PROCESSOR_TICKETS.PHONE_NUMBER)
                        .orderBy(DSL.field(DSL.name(CONVERSATION_ORDERED_AT)).desc())
                        .limit(pageable.getPageSize())
                        .offset((int) pageable.getOffset()))
                .map(rec -> {
                    // DIAL_CODE is SMALLINT, so jOOQ hands back a Short while the DTO carries an
                    // Integer.
                    Short dialCode = rec.get(ENTITY_PROCESSOR_TICKETS.DIAL_CODE);
                    return new WhatsappConversationResponse()
                            .setDialCode(dialCode != null ? dialCode.intValue() : null)
                            .setPhoneNumber(rec.get(ENTITY_PROCESSOR_TICKETS.PHONE_NUMBER))
                            .setOrderedAt(rec.get(orderedAt))
                            .setLastMessageAt(rec.get(lastMessageAt));
                })
                .collectList();
    }

    /** Hydrates the page's numbers with the deals behind them, still under the access condition. */
    private Mono<List<WhatsappConversationResponse>> attachDeals(
            Condition where, List<WhatsappConversationResponse> conversations) {

        if (conversations.isEmpty()) return Mono.just(conversations);

        // Matched on number alone, not (dialCode, number). A row-value IN would be exact, but the
        // dial code is nullable and jOOQ types it Short against an Integer on the DTO, so the pair
        // comparison costs more than it buys. The Java grouping below still keys on both, so an
        // over-fetched row from a different dial code simply matches no conversation.
        List<String> numbers = conversations.stream()
                .map(WhatsappConversationResponse::getPhoneNumber)
                .toList();

        return Flux.from(this.dslContext
                        .selectFrom(this.table)
                        .where(where)
                        .and(ENTITY_PROCESSOR_TICKETS.PHONE_NUMBER.in(numbers))
                        .orderBy(DSL.coalesce(
                                        ENTITY_PROCESSOR_TICKETS.LAST_MESSAGE_AT,
                                        ENTITY_PROCESSOR_TICKETS.UPDATED_AT)
                                .desc()))
                .map(rec -> rec.into(this.pojoClass))
                .collectList()
                .map(tickets -> {
                    Map<String, List<Ticket>> byNumber = tickets.stream()
                            .collect(Collectors.groupingBy(
                                    t -> conversationKey(t.getDialCode(), t.getPhoneNumber()),
                                    LinkedHashMap::new,
                                    Collectors.toList()));

                    conversations.forEach(conversation -> {
                        List<Ticket> deals = byNumber.getOrDefault(
                                conversationKey(conversation.getDialCode(), conversation.getPhoneNumber()),
                                List.of());
                        conversation.setDeals(deals.stream()
                                .map(WhatsappConversationResponse.Deal::of)
                                .toList());
                        if (!deals.isEmpty()) conversation.setPrimaryTicketId(deals.getFirst().getId());

                        // The first deal that actually has one, rather than the first deal's value.
                        // Every deal on a number is written the same avatar, but a deal created
                        // before the customer's first message has never been written at all, and
                        // taking the primary blindly would show a blank circle next to a face.
                        deals.stream()
                                .map(Ticket::getWhatsappProfilePicFileDetail)
                                .filter(Objects::nonNull)
                                .findFirst()
                                .ifPresent(conversation::setProfilePicFileDetail);
                    });

                    return conversations;
                });
    }

    private String conversationKey(Integer dialCode, String phoneNumber) {
        return dialCode + "|" + phoneNumber;
    }

    /**
     * Records a customer's WhatsApp avatar against every deal on their number.
     *
     * <p>Every deal, not just the one that happened to receive the message. A customer can hold
     * several, the conversation is already a union across them, and writing the picture to one would
     * show the same person with a face on one deal and a blank circle on the next.
     *
     * <p>Matched on the plain phone number within the tenant, which is how the rest of this service
     * identifies a customer, and covered by IDX9 added alongside these columns.
     *
     * <p>A null detail is a real instruction rather than a no-op: the customer removed their picture
     * and what is held must be cleared.
     */
    public Mono<Integer> updateWhatsappProfilePicture(
            String appCode, String clientCode, String phoneNumber, FileDetail detail, String pictureId) {

        if (StringUtil.safeIsBlank(phoneNumber)) return Mono.just(0);

        return Mono.from(this.dslContext
                .update(ENTITY_PROCESSOR_TICKETS)
                .set(ENTITY_PROCESSOR_TICKETS.WHATSAPP_PROFILE_PIC_FILE_DETAIL, detail)
                .set(ENTITY_PROCESSOR_TICKETS.WHATSAPP_PROFILE_PIC_ID, pictureId)
                .where(ENTITY_PROCESSOR_TICKETS.APP_CODE.eq(appCode))
                .and(ENTITY_PROCESSOR_TICKETS.CLIENT_CODE.eq(clientCode))
                // Either number, for the same reason the inbound match uses both: the avatar arrives
                // keyed on the number the customer messages from, which is the WhatsApp number
                // whenever someone has recorded one. Matching on the phone alone would leave exactly
                // the corrected deals without a face.
                .and(ENTITY_PROCESSOR_TICKETS
                        .PHONE_NUMBER
                        .eq(phoneNumber)
                        .or(ENTITY_PROCESSOR_TICKETS.WHATSAPP_NUMBER.eq(phoneNumber))))
                .defaultIfEmpty(0);
    }
}
