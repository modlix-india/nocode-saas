package com.fincity.saas.entity.processor.analytics.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorStages.ENTITY_PROCESSOR_STAGES;
import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.entity.processor.analytics.dao.base.BaseAnalyticsDAO;
import com.fincity.saas.entity.processor.analytics.enums.TimePeriod;
import com.fincity.saas.entity.processor.analytics.model.TicketBucketFilter;
import com.fincity.saas.entity.processor.analytics.model.base.BaseFilter;
import com.fincity.saas.entity.processor.analytics.model.common.PerDateCount;
import com.fincity.saas.entity.processor.analytics.model.common.PerValueCount;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Select;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class TicketBucketDAO extends BaseAnalyticsDAO<EntityProcessorTicketsRecord, Ticket> {

    private static final String NO_STAGE = "No Stage";

    private static final String NO_STATUS = "No Status";

    private static final String TOTAL = "Total";

    protected TicketBucketDAO() {
        super(Ticket.class, ENTITY_PROCESSOR_TICKETS, ENTITY_PROCESSOR_TICKETS.ID);
    }

    @Override
    protected Map<String, String> getBucketFilterFieldMappings() {
        return Map.of(
                BaseFilter.Fields.createdByIds, AbstractDTO.Fields.createdBy,
                BaseFilter.Fields.assignedUserIds, Ticket.Fields.assignedUserId,
                BaseFilter.Fields.clientIds, BaseProcessorDto.Fields.clientId,
                TicketBucketFilter.Fields.sources, Ticket.Fields.source,
                TicketBucketFilter.Fields.subSources, Ticket.Fields.subSource,
                TicketBucketFilter.Fields.stageIds, Ticket.Fields.stage,
                TicketBucketFilter.Fields.statusIds, Ticket.Fields.status,
                TicketBucketFilter.Fields.productIds, Ticket.Fields.productId,
                BaseFilter.Fields.startDate, AbstractDTO.Fields.createdAt,
                BaseFilter.Fields.endDate, AbstractDTO.Fields.createdAt);
    }

    private Mono<AbstractCondition> createTicketBucketConditions(
            ProcessorAccess access, TicketBucketFilter ticketBucketFilter) {
        return Mono.zip(
                        super.createBucketConditions(access, ticketBucketFilter),
                        this.createTicketConditions(ticketBucketFilter))
                .map(conds -> ComplexCondition.and(conds.getT1(), conds.getT2()));
    }

    private Mono<AbstractCondition> createTicketConditions(TicketBucketFilter filter) {

        Map<String, String> fieldMappings = this.getBucketFilterFieldMappings();

        if (filter == null) filter = new TicketBucketFilter();

        return Mono.zip(
                        this.ticketFilterCondition(),
                        this.getSourceConditions(filter, fieldMappings)
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()),
                        this.getStageConditions(filter, fieldMappings)
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()),
                        this.getStatusConditions(filter, fieldMappings)
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()),
                        this.getProductConditions(filter, fieldMappings)
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()))
                .map(condTuple -> {
                    List<AbstractCondition> conditions = new ArrayList<>();

                    conditions.add(condTuple.getT1());
                    condTuple.getT2().ifPresent(conditions::add);
                    condTuple.getT3().ifPresent(conditions::add);
                    condTuple.getT4().ifPresent(conditions::add);
                    condTuple.getT5().ifPresent(conditions::add);

                    return ComplexCondition.and(conditions.stream()
                            .filter(AbstractCondition::isNonEmpty)
                            .toList());
                });
    }

    private Mono<AbstractCondition> ticketFilterCondition() {
        return Mono.just(new FilterCondition()
                .setField(BaseUpdatableDto.Fields.isActive)
                .setOperator(FilterConditionOperator.IS_TRUE));
    }

    private Mono<AbstractCondition> getSourceConditions(TicketBucketFilter filter, Map<String, String> fieldMappings) {
        return Mono.zip(
                        super.makeIn(fieldMappings.get(TicketBucketFilter.Fields.sources), filter.getSources())
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()),
                        super.makeIn(fieldMappings.get(TicketBucketFilter.Fields.subSources), filter.getSubSources())
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()))
                .map(sourceSubSourceTup -> {
                    List<AbstractCondition> conditions = new ArrayList<>();

                    sourceSubSourceTup.getT1().ifPresent(conditions::add);
                    sourceSubSourceTup.getT2().ifPresent(conditions::add);

                    return ComplexCondition.and(conditions.stream()
                            .filter(AbstractCondition::isNonEmpty)
                            .toList());
                });
    }

    private Mono<AbstractCondition> getStageConditions(TicketBucketFilter filter, Map<String, String> fieldMappings) {
        return this.makeIn(fieldMappings.get(TicketBucketFilter.Fields.stageIds), filter.getStageIds());
    }

    private Mono<AbstractCondition> getStatusConditions(TicketBucketFilter filter, Map<String, String> fieldMappings) {
        return this.makeIn(fieldMappings.get(TicketBucketFilter.Fields.statusIds), filter.getStatusIds());
    }

    private Mono<AbstractCondition> getProductConditions(TicketBucketFilter filter, Map<String, String> fieldMappings) {
        return this.makeIn(fieldMappings.get(TicketBucketFilter.Fields.productIds), filter.getProductIds());
    }

    public Flux<PerValueCount> getTicketPerAssignedUserStageCount(
            ProcessorAccess access, TicketBucketFilter ticketBucketFilter) {
        return this.getTicketCountByGroupAndJoin(
                access,
                ticketBucketFilter,
                ENTITY_PROCESSOR_TICKETS.ASSIGNED_USER_ID,
                ENTITY_PROCESSOR_TICKETS.STAGE,
                NO_STAGE,
                false);
    }

    public Flux<PerValueCount> getTicketPerCreatedByStageCount(
            ProcessorAccess access, TicketBucketFilter ticketBucketFilter) {
        return this.getTicketCountByGroupAndJoin(
                access,
                ticketBucketFilter,
                ENTITY_PROCESSOR_TICKETS.CREATED_BY,
                ENTITY_PROCESSOR_TICKETS.STAGE,
                NO_STAGE,
                true);
    }

    public Flux<PerValueCount> getTicketPerClientIdStageCount(
            ProcessorAccess access, TicketBucketFilter ticketBucketFilter) {
        return this.getTicketCountByGroupAndJoin(
                access,
                ticketBucketFilter,
                ENTITY_PROCESSOR_TICKETS.CLIENT_ID,
                ENTITY_PROCESSOR_TICKETS.STAGE,
                NO_STAGE,
                true);
    }

    public Flux<PerValueCount> getTicketPerProjectStageCount(
            ProcessorAccess access, TicketBucketFilter ticketBucketFilter) {
        return this.getTicketCountByGroupAndJoin(
                access,
                ticketBucketFilter,
                ENTITY_PROCESSOR_TICKETS.PRODUCT_ID,
                ENTITY_PROCESSOR_TICKETS.STAGE,
                NO_STAGE,
                false);
    }

    public Flux<PerValueCount> getTicketPerAssignedUserStatusCount(
            ProcessorAccess access, TicketBucketFilter ticketBucketFilter) {
        return this.getTicketCountByGroupAndJoin(
                access,
                ticketBucketFilter,
                ENTITY_PROCESSOR_TICKETS.ASSIGNED_USER_ID,
                ENTITY_PROCESSOR_TICKETS.STATUS,
                NO_STATUS,
                false);
    }

    public Flux<PerValueCount> getTicketPerCreatedByStatusCount(
            ProcessorAccess access, TicketBucketFilter ticketBucketFilter) {
        return this.getTicketCountByGroupAndJoin(
                access,
                ticketBucketFilter,
                ENTITY_PROCESSOR_TICKETS.CREATED_BY,
                ENTITY_PROCESSOR_TICKETS.STATUS,
                NO_STATUS,
                true);
    }

    public Flux<PerValueCount> getTicketPerClientIdStatusCount(
            ProcessorAccess access, TicketBucketFilter ticketBucketFilter) {
        return this.getTicketCountByGroupAndJoin(
                access,
                ticketBucketFilter,
                ENTITY_PROCESSOR_TICKETS.CLIENT_ID,
                ENTITY_PROCESSOR_TICKETS.STATUS,
                NO_STATUS,
                true);
    }

    public Flux<PerValueCount> getTicketPerProjectStatusCount(
            ProcessorAccess access, TicketBucketFilter ticketBucketFilter) {
        return this.getTicketCountByGroupAndJoin(
                access,
                ticketBucketFilter,
                ENTITY_PROCESSOR_TICKETS.PRODUCT_ID,
                ENTITY_PROCESSOR_TICKETS.STATUS,
                NO_STATUS,
                false);
    }

    private Mono<reactor.util.function.Tuple2<Condition, Condition>> resolveFilteredAndTotalConditions(
            AbstractCondition abstractCondition, TicketBucketFilter ticketBucketFilter) {

        if (ticketBucketFilter != null && ticketBucketFilter.isIncludeTotal()) {
            return abstractCondition
                    .removeConditionWithField(Ticket.Fields.stage)
                    .defaultIfEmpty(abstractCondition)
                    .flatMap(totalCondition -> Mono.zip(super.filter(abstractCondition), super.filter(totalCondition)));
        }

        return super.filter(abstractCondition)
                .map(conditions -> reactor.util.function.Tuples.of(conditions, conditions));
    }

    private Flux<PerValueCount> getTicketCountByGroupAndJoin(
            ProcessorAccess access,
            TicketBucketFilter ticketBucketFilter,
            Field<ULong> groupField,
            Field<ULong> joinField,
            String defaultValue,
            boolean requiresNonNull) {

        return FlatMapUtil.flatMapFlux(
                () -> this.createTicketBucketConditions(access, ticketBucketFilter)
                        .flux(),
                abstractCondition -> this.resolveFilteredAndTotalConditions(abstractCondition, ticketBucketFilter)
                        .flux(),
                (abstractCondition, conditionsTuple) -> {
                    var conditions = conditionsTuple.getT1();
                    var totalConditions = conditionsTuple.getT2();

                    Select<?> select = ticketBucketFilter.isIncludeTotal()
                            ? this.unionAsRecord(
                                    this.buildGroupedStageJoinCountSelect(
                                                    groupField, joinField, conditions, requiresNonNull)
                                            .groupBy(groupField, ENTITY_PROCESSOR_STAGES.NAME),
                                    this.buildTotalOnlyStageCountSelect(groupField, totalConditions, requiresNonNull)
                                            .groupBy(groupField))
                            : this.buildGroupedStageJoinCountSelect(groupField, joinField, conditions, requiresNonNull)
                                    .groupBy(groupField, ENTITY_PROCESSOR_STAGES.NAME);

                    return Flux.from(select).map(rec -> this.mapToPerValueCount(rec, groupField, defaultValue));
                });
    }

    private SelectConditionStep<?> buildGroupedStageJoinCountSelect(
            Field<ULong> groupField, Field<ULong> joinField, Condition conditions, boolean requiresNonNull) {

        SelectConditionStep<?> query = this.dslContext
                .select(groupField, ENTITY_PROCESSOR_STAGES.NAME, DSL.count(this.idField))
                .from(this.table)
                .leftJoin(ENTITY_PROCESSOR_STAGES)
                .on(joinField.eq(ENTITY_PROCESSOR_STAGES.ID))
                .where(conditions);

        return requiresNonNull ? query.and(groupField.isNotNull()) : query;
    }

    private SelectConditionStep<?> buildTotalOnlyStageCountSelect(
            Field<ULong> groupField, Condition conditions, boolean requiresNonNull) {

        SelectConditionStep<?> query = this.dslContext
                .select(groupField, DSL.val(TOTAL).as(ENTITY_PROCESSOR_STAGES.NAME), DSL.count(this.idField))
                .from(this.table)
                .where(conditions);

        return requiresNonNull ? query.and(groupField.isNotNull()) : query;
    }

    private PerValueCount mapToPerValueCount(Record rec, Field<ULong> groupField, String defaultValue) {
        String stageName = rec.get(ENTITY_PROCESSOR_STAGES.NAME);
        if (stageName == null) stageName = defaultValue;

        return new PerValueCount()
                .setGroupedId(rec.get(groupField))
                .setMapValue(stageName)
                .setCount(rec.get(DSL.count(this.idField)).longValue());
    }

    public Flux<PerDateCount> getTicketPerAssignedUserStageSourceDateCount(
            ProcessorAccess access, TicketBucketFilter ticketBucketFilter) {
        return this.getTicketDateCountByGroupAndJoin(
                access,
                ticketBucketFilter,
                ENTITY_PROCESSOR_TICKETS.SOURCE,
                ENTITY_PROCESSOR_TICKETS.STAGE,
                ENTITY_PROCESSOR_TICKETS.CREATED_AT,
                NO_STAGE,
                false);
    }

    private Flux<PerDateCount> getTicketDateCountByGroupAndJoin(
            ProcessorAccess access,
            TicketBucketFilter ticketBucketFilter,
            Field<String> groupField,
            Field<ULong> joinField,
            Field<LocalDateTime> dateField,
            String defaultValue,
            boolean requiresNonNull) {

        Field<LocalDate> castedDateField = DSL.cast(dateField, SQLDataType.LOCALDATE);

        return FlatMapUtil.flatMapFlux(
                () -> this.createTicketBucketConditions(access, ticketBucketFilter)
                        .flux(),
                abstractCondition -> this.resolveFilteredAndTotalConditions(abstractCondition, ticketBucketFilter)
                        .flux(),
                (abstractCondition, conditionsTuple) -> {
                    var conditions = conditionsTuple.getT1();
                    var totalConditions = conditionsTuple.getT2();

                    Select<?> select = ticketBucketFilter.isIncludeTotal()
                            ? this.unionAsRecord(
                                    this.buildGroupedStageJoinDateCountSelect(
                                                    groupField, joinField, castedDateField, conditions, requiresNonNull)
                                            .groupBy(groupField, ENTITY_PROCESSOR_STAGES.NAME, castedDateField),
                                    this.buildTotalOnlyDateCountSelect(
                                                    groupField, castedDateField, totalConditions, requiresNonNull)
                                            .groupBy(groupField, castedDateField))
                            : this.buildGroupedStageJoinDateCountSelect(
                                            groupField, joinField, castedDateField, conditions, requiresNonNull)
                                    .groupBy(groupField, ENTITY_PROCESSOR_STAGES.NAME, castedDateField);

                    return Flux.from(select)
                            .map(rec -> this.mapToPerDateCount(rec, groupField, castedDateField, defaultValue));
                });
    }

    private SelectConditionStep<?> buildGroupedStageJoinDateCountSelect(
            Field<String> groupField,
            Field<ULong> joinField,
            Field<LocalDate> castedDateField,
            Condition conditions,
            boolean requiresNonNull) {

        SelectConditionStep<?> query = this.dslContext
                .select(ENTITY_PROCESSOR_STAGES.NAME, groupField, castedDateField, DSL.count(this.idField))
                .from(this.table)
                .leftJoin(ENTITY_PROCESSOR_STAGES)
                .on(joinField.eq(ENTITY_PROCESSOR_STAGES.ID))
                .where(conditions);

        return requiresNonNull ? query.and(groupField.isNotNull()) : query;
    }

    private SelectConditionStep<?> buildTotalOnlyDateCountSelect(
            Field<String> groupField, Field<LocalDate> castedDateField, Condition conditions, boolean requiresNonNull) {

        SelectConditionStep<?> query = this.dslContext
                .select(
                        DSL.val(TOTAL).as(ENTITY_PROCESSOR_STAGES.NAME),
                        groupField,
                        castedDateField,
                        DSL.count(this.idField))
                .from(this.table)
                .where(conditions);

        return requiresNonNull ? query.and(groupField.isNotNull()) : query;
    }

    private PerDateCount mapToPerDateCount(
            Record rec, Field<String> groupField, Field<LocalDate> castedDateField, String defaultValue) {

        String stageName = rec.get(ENTITY_PROCESSOR_STAGES.NAME);
        if (stageName == null) stageName = defaultValue;

        return new PerDateCount()
                .setDate(rec.get(castedDateField))
                .setMapValue(stageName)
                .setGroupedValue(rec.get(groupField))
                .setCount(rec.get(DSL.count(this.idField)).longValue());
    }

    public Flux<PerDateCount> getTicketCountPerStageAndDateWithClientId(
            ProcessorAccess access, TicketBucketFilter ticketBucketFilter, TimePeriod timePeriod) {
        return this.getTicketDateCountByStageOnly(
                access,
                ticketBucketFilter,
                ENTITY_PROCESSOR_TICKETS.STAGE,
                timePeriod,
                ENTITY_PROCESSOR_TICKETS.CREATED_AT,
                DSL.count(this.idField),
                NO_STAGE,
                Boolean.FALSE,
                Boolean.TRUE);
    }

    private Flux<PerDateCount> getTicketDateCountByStageOnly( // NOSONAR
            ProcessorAccess access,
            TicketBucketFilter ticketBucketFilter,
            Field<ULong> joinField,
            TimePeriod timePeriod,
            Field<LocalDateTime> dateField,
            Field<? extends Number> countField,
            String defaultValue,
            boolean requiresCreatedByNotNull,
            Boolean requiresClientIdNotNull) {

        Field<LocalDate> castedDateField = this.toDateBucketField(timePeriod, dateField);

        return FlatMapUtil.flatMapFlux(
                () -> this.createTicketBucketConditions(access, ticketBucketFilter)
                        .flux(),
                abstractCondition -> this.resolveFilteredAndTotalConditions(abstractCondition, ticketBucketFilter)
                        .flux(),
                (abstractCondition, conditionsTuple) -> {
                    var conditions = conditionsTuple.getT1();
                    var totalConditions = conditionsTuple.getT2();

                    Select<?> select = ticketBucketFilter.isIncludeTotal()
                            ? this.unionAsRecord(
                                    this.buildStageOnlyDateCountSelect(
                                                    joinField,
                                                    castedDateField,
                                                    countField,
                                                    conditions,
                                                    requiresCreatedByNotNull,
                                                    requiresClientIdNotNull)
                                            .groupBy(castedDateField, ENTITY_PROCESSOR_STAGES.NAME),
                                    this.buildTotalOnlyStageDateCountSelect(
                                                    castedDateField,
                                                    countField,
                                                    totalConditions,
                                                    requiresCreatedByNotNull,
                                                    requiresClientIdNotNull)
                                            .groupBy(castedDateField))
                            : this.buildStageOnlyDateCountSelect(
                                            joinField,
                                            castedDateField,
                                            countField,
                                            conditions,
                                            requiresCreatedByNotNull,
                                            requiresClientIdNotNull)
                                    .groupBy(castedDateField, ENTITY_PROCESSOR_STAGES.NAME);

                    return Flux.from(select)
                            .map(rec ->
                                    this.mapToPerStageOnlyDateCount(rec, castedDateField, countField, defaultValue));
                });
    }

    private SelectConditionStep<?> buildStageOnlyDateCountSelect(
            Field<ULong> joinField,
            Field<LocalDate> castedDateField,
            Field<? extends Number> countField,
            Condition conditions,
            boolean requiresCreatedByNotNull,
            Boolean requiresClientIdNotNull) {

        SelectConditionStep<?> query = this.dslContext
                .select(ENTITY_PROCESSOR_STAGES.NAME, castedDateField, countField)
                .from(this.table)
                .leftJoin(ENTITY_PROCESSOR_STAGES)
                .on(joinField.eq(ENTITY_PROCESSOR_STAGES.ID))
                .where(conditions);

        return this.applyTicketNotNullFilters(query, requiresCreatedByNotNull, requiresClientIdNotNull);
    }

    private SelectConditionStep<?> buildTotalOnlyStageDateCountSelect(
            Field<LocalDate> castedDateField,
            Field<? extends Number> countField,
            Condition conditions,
            boolean requiresCreatedByNotNull,
            Boolean requiresClientIdNotNull) {

        SelectConditionStep<?> query = this.dslContext
                .select(DSL.val(TOTAL).as(ENTITY_PROCESSOR_STAGES.NAME), castedDateField, countField)
                .from(this.table)
                .where(conditions);

        return this.applyTicketNotNullFilters(query, requiresCreatedByNotNull, requiresClientIdNotNull);
    }

    private SelectConditionStep<?> applyTicketNotNullFilters(
            SelectConditionStep<?> query, boolean requiresCreatedByNotNull, Boolean requiresClientIdNotNull) {

        if (requiresCreatedByNotNull) query = query.and(ENTITY_PROCESSOR_TICKETS.CREATED_BY.isNotNull());

        if (Boolean.TRUE.equals(requiresClientIdNotNull))
            query = query.and(ENTITY_PROCESSOR_TICKETS.CLIENT_ID.isNotNull());

        return query;
    }

    private PerDateCount mapToPerStageOnlyDateCount(
            Record rec, Field<LocalDate> castedDateField, Field<? extends Number> countField, String defaultValue) {

        String stageName = rec.get(ENTITY_PROCESSOR_STAGES.NAME);
        if (stageName == null) stageName = defaultValue;

        return new PerDateCount()
                .setDate(rec.get(castedDateField))
                .setMapValue(stageName)
                .setGroupedValue(null)
                .setCount(rec.get(countField).longValue());
    }

    public Flux<PerDateCount> getUniqueCreatedByCountPerStageAndDateWithClientId(
            ProcessorAccess access, TicketBucketFilter ticketBucketFilter, TimePeriod timePeriod) {
        Field<? extends Number> uniqueCountField =
                DSL.countDistinct(ENTITY_PROCESSOR_TICKETS.CLIENT_ID).as("uniqueClientCount");

        return this.getTicketDateCountByStageOnly(
                access,
                ticketBucketFilter,
                ENTITY_PROCESSOR_TICKETS.STAGE,
                timePeriod,
                ENTITY_PROCESSOR_TICKETS.CREATED_AT,
                uniqueCountField,
                NO_STAGE,
                Boolean.FALSE,
                Boolean.TRUE);
    }

    private Field<LocalDate> toDateBucketField(TimePeriod timePeriod, Field<LocalDateTime> dateTimeField) {

        if (timePeriod == null) return DSL.cast(dateTimeField, SQLDataType.LOCALDATE);

        return switch (timePeriod) {
            case WEEKS ->
                DSL.field(
                        "date_sub(cast({0} as date), interval weekday(cast({0} as date)) day)",
                        SQLDataType.LOCALDATE, dateTimeField);
            case MONTHS ->
                DSL.field(
                        "str_to_date(date_format({0}, '%Y-%m-01'), '%Y-%m-%d')", SQLDataType.LOCALDATE, dateTimeField);
            case QUARTERS ->
                DSL.field(
                        "str_to_date(concat(year({0}), '-', lpad(((quarter({0})-1)*3)+1, 2, '0'), '-01'), '%Y-%m-%d')",
                        SQLDataType.LOCALDATE, dateTimeField);
            case YEARS ->
                DSL.field(
                        "str_to_date(date_format({0}, '%Y-01-01'), '%Y-%m-%d')", SQLDataType.LOCALDATE, dateTimeField);
            default -> DSL.cast(dateTimeField, SQLDataType.LOCALDATE);
        };
    }

    public Flux<PerValueCount> getTicketCountPerProductStageAndClientId(
            ProcessorAccess access, TicketBucketFilter ticketBucketFilter) {
        Field<String> clientIdAsString = DSL.cast(ENTITY_PROCESSOR_TICKETS.CLIENT_ID, SQLDataType.VARCHAR);

        return FlatMapUtil.flatMapFlux(
                () -> this.createTicketBucketConditions(access, ticketBucketFilter)
                        .flux(),
                abstractCondition -> super.filter(abstractCondition).flux(),
                (abstractCondition, conditions) -> {
                    SelectConditionStep<?> query = this.dslContext
                            .select(ENTITY_PROCESSOR_TICKETS.PRODUCT_ID, clientIdAsString, DSL.count(this.idField))
                            .from(this.table)
                            .where(conditions.and(ENTITY_PROCESSOR_TICKETS.CLIENT_ID.isNotNull()));

                    return Flux.from(query.groupBy(ENTITY_PROCESSOR_TICKETS.PRODUCT_ID, clientIdAsString))
                            .map(rec -> new PerValueCount()
                                    .setGroupedId(rec.get(ENTITY_PROCESSOR_TICKETS.PRODUCT_ID))
                                    .setGroupedValue(rec.get(clientIdAsString))
                                    .setCount(rec.get(DSL.count(this.idField)).longValue()));
                });
    }

    public Flux<PerDateCount> getTicketCountPerClientIdAndDate(
            ProcessorAccess access, TicketBucketFilter ticketBucketFilter) {
        Field<String> clientIdAsString = DSL.cast(ENTITY_PROCESSOR_TICKETS.CLIENT_ID, SQLDataType.VARCHAR);
        Field<LocalDate> castedDateField = DSL.cast(ENTITY_PROCESSOR_TICKETS.CREATED_AT, SQLDataType.LOCALDATE);

        return FlatMapUtil.flatMapFlux(
                () -> this.createTicketBucketConditions(access, ticketBucketFilter)
                        .flux(),
                abstractCondition -> super.filter(abstractCondition).flux(),
                (abstractCondition, conditions) -> {
                    SelectConditionStep<?> query = this.dslContext
                            .select(clientIdAsString, castedDateField, DSL.count(this.idField))
                            .from(this.table)
                            .where(conditions.and(ENTITY_PROCESSOR_TICKETS.CLIENT_ID.isNotNull()));

                    return Flux.from(query.groupBy(clientIdAsString, castedDateField))
                            .map(rec -> new PerDateCount()
                                    .setDate(rec.get(castedDateField))
                                    .setGroupedValue(rec.get(clientIdAsString))
                                    .setCount(rec.get(DSL.count(this.idField)).longValue()));
                });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Select<?> unionAsRecord(Select<?> left, Select<?> right) {
        return (left).union((Select) right);
    }
}
