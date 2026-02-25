package com.fincity.saas.entity.processor.analytics.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorActivities.ENTITY_PROCESSOR_ACTIVITIES;
import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorStages.ENTITY_PROCESSOR_STAGES;
import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.entity.processor.analytics.dao.base.BaseAnalyticsDAO;
import com.fincity.saas.entity.processor.analytics.enums.TimePeriod;
import com.fincity.saas.entity.processor.analytics.model.TicketBucketFilter;
import com.fincity.saas.entity.processor.analytics.model.base.BaseFilter;
import com.fincity.saas.entity.processor.analytics.model.common.PerDateCount;
import com.fincity.saas.entity.processor.analytics.model.common.PerValueCount;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.enums.ActivityAction;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
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
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Component
public class TicketBucketDAO extends BaseAnalyticsDAO<EntityProcessorTicketsRecord, Ticket> {

    private static final String NO_STAGE = "No Stage";

    private static final String NO_STATUS = "No Status";

    private static final String TOTAL = "Total";

    private record ActivityQueryConfig(
            Condition ticketConditions,
            LocalDateTime startDate,
            LocalDateTime endDate,
            List<ULong> stageIds,
            boolean requiresGroupFieldNotNull,
            boolean requiresCreatedByNotNull,
            Boolean requiresClientIdNotNull) {}

    private record TicketConditionOptions(boolean includeStage, boolean includeStatus) {
        static final TicketConditionOptions ALL = new TicketConditionOptions(true, true);
        static final TicketConditionOptions WITHOUT_STAGE = new TicketConditionOptions(false, true);
        static final TicketConditionOptions WITHOUT_STAGE_OR_STATUS = new TicketConditionOptions(false, false);
    }

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
                        this.createTicketConditions(ticketBucketFilter, TicketConditionOptions.ALL))
                .map(conds -> ComplexCondition.and(conds.getT1(), conds.getT2()));
    }

    private Mono<AbstractCondition> createTicketBucketConditionsWithoutDate(
            ProcessorAccess access, TicketBucketFilter ticketBucketFilter) {
        return Mono.zip(
                        super.createBucketConditionsWithoutDate(access, ticketBucketFilter),
                        this.createTicketConditions(ticketBucketFilter, TicketConditionOptions.WITHOUT_STAGE))
                .map(conds -> ComplexCondition.and(conds.getT1(), conds.getT2()));
    }

    private Mono<AbstractCondition> createTotalCountConditions(
            ProcessorAccess access, TicketBucketFilter ticketBucketFilter) {
        return Mono.zip(
                        super.createBucketConditions(access, ticketBucketFilter),
                        this.createTicketConditions(ticketBucketFilter, TicketConditionOptions.WITHOUT_STAGE_OR_STATUS))
                .map(conds -> ComplexCondition.and(conds.getT1(), conds.getT2()));
    }

    private Mono<AbstractCondition> createTicketConditions(TicketBucketFilter filter, TicketConditionOptions options) {
        Map<String, String> fieldMappings = this.getBucketFilterFieldMappings();
        if (filter == null) filter = new TicketBucketFilter();

        List<Mono<Optional<AbstractCondition>>> conditionMonos = new ArrayList<>();
        conditionMonos.add(this.ticketFilterCondition().map(Optional::of));
        conditionMonos.add(this.getSourceConditions(filter, fieldMappings)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty()));
        conditionMonos.add(this.getProductConditions(filter, fieldMappings)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty()));

        if (options.includeStage()) {
            conditionMonos.add(this.getStageConditions(filter, fieldMappings)
                    .map(Optional::of)
                    .defaultIfEmpty(Optional.empty()));
        }
        if (options.includeStatus()) {
            conditionMonos.add(this.getStatusConditions(filter, fieldMappings)
                    .map(Optional::of)
                    .defaultIfEmpty(Optional.empty()));
        }

        return Mono.zip(conditionMonos, this::combineConditions);
    }

    private AbstractCondition combineConditions(Object[] results) {
        List<AbstractCondition> conditions = new ArrayList<>();
        for (Object result : results) {
            @SuppressWarnings("unchecked")
            Optional<AbstractCondition> opt = (Optional<AbstractCondition>) result;
            opt.ifPresent(conditions::add);
        }
        return ComplexCondition.and(
                conditions.stream().filter(AbstractCondition::isNonEmpty).toList());
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
        return this.getTicketStageCountByGroupActivityBased(
                access, ticketBucketFilter, ENTITY_PROCESSOR_TICKETS.ASSIGNED_USER_ID, NO_STAGE, false);
    }

    public Flux<PerValueCount> getTicketPerCreatedByStageCount(
            ProcessorAccess access, TicketBucketFilter ticketBucketFilter) {
        return this.getTicketStageCountByGroupActivityBased(
                access, ticketBucketFilter, ENTITY_PROCESSOR_TICKETS.CREATED_BY, NO_STAGE, true);
    }

    public Flux<PerValueCount> getTicketPerClientIdStageCount(
            ProcessorAccess access, TicketBucketFilter ticketBucketFilter) {
        return this.getTicketStageCountByGroupActivityBased(
                access, ticketBucketFilter, ENTITY_PROCESSOR_TICKETS.CLIENT_ID, NO_STAGE, true);
    }

    public Flux<PerValueCount> getTicketPerProjectStageCount(
            ProcessorAccess access, TicketBucketFilter ticketBucketFilter) {
        return this.getTicketStageCountByGroupActivityBased(
                access, ticketBucketFilter, ENTITY_PROCESSOR_TICKETS.PRODUCT_ID, NO_STAGE, false);
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

    private Mono<Tuple2<Condition, Condition>> resolveFilteredAndTotalConditions(
            AbstractCondition abstractCondition, TicketBucketFilter ticketBucketFilter) {

        if (ticketBucketFilter != null && ticketBucketFilter.isIncludeTotal()) {
            return abstractCondition
                    .removeConditionWithField(Ticket.Fields.stage)
                    .defaultIfEmpty(abstractCondition)
                    .flatMap(totalCondition -> Mono.zip(super.filter(abstractCondition), super.filter(totalCondition)));
        }

        return super.filter(abstractCondition).map(conditions -> Tuples.of(conditions, conditions));
    }

    private Mono<Tuple3<Condition, Condition, TicketBucketFilter>> resolveActivityBasedStageConditions(
            ProcessorAccess access, TicketBucketFilter ticketBucketFilter) {

        return Mono.zip(
                        this.createTicketBucketConditionsWithoutDate(access, ticketBucketFilter)
                                .flatMap(super::filter),
                        this.createTotalCountConditions(access, ticketBucketFilter)
                                .flatMap(super::filter))
                .map(condTuple -> Tuples.of(condTuple.getT1(), condTuple.getT2(), ticketBucketFilter));
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

                    return Flux.from(select).map(rec -> this.mapToPerValueCount(rec, groupField, defaultValue, false));
                });
    }

    private Flux<PerValueCount> getTicketStageCountByGroupActivityBased(
            ProcessorAccess access,
            TicketBucketFilter ticketBucketFilter,
            Field<ULong> groupField,
            String defaultValue,
            boolean requiresNonNull) {

        return FlatMapUtil.flatMapFlux(
                () -> this.resolveActivityBasedStageConditions(access, ticketBucketFilter)
                        .flux(),
                conditionsTuple -> {
                    var stageConditions = conditionsTuple.getT1();
                    var totalConditions = conditionsTuple.getT2();
                    var filter = conditionsTuple.getT3();

                    ActivityQueryConfig config = new ActivityQueryConfig(
                            stageConditions,
                            filter.getStartDate(),
                            filter.getEndDate(),
                            filter.getStageIds(),
                            requiresNonNull,
                            false,
                            null);

                    Select<?> select = filter.isIncludeTotal()
                            ? this.unionAsRecord(
                                    this.buildActivityBasedStageCountSelect(groupField, config)
                                            .groupBy(groupField, ENTITY_PROCESSOR_STAGES.NAME),
                                    this.buildTotalOnlyStageCountSelect(groupField, totalConditions, requiresNonNull)
                                            .groupBy(groupField))
                            : this.buildActivityBasedStageCountSelect(groupField, config)
                                    .groupBy(groupField, ENTITY_PROCESSOR_STAGES.NAME);

                    return Flux.from(select).map(rec -> this.mapToPerValueCount(rec, groupField, defaultValue, true));
                });
    }

    private PerValueCount mapToPerValueCount(
            Record rec, Field<ULong> groupField, String defaultValue, boolean useDistinct) {
        String stageName = rec.get(ENTITY_PROCESSOR_STAGES.NAME);
        if (stageName == null) stageName = defaultValue;

        Field<? extends Number> countField = useDistinct ? DSL.countDistinct(this.idField) : DSL.count(this.idField);

        return new PerValueCount()
                .setGroupedId(rec.get(groupField))
                .setMapValue(stageName)
                .setCount(rec.get(countField).longValue());
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

    private SelectConditionStep<?> buildActivityBasedStageCountSelect(
            Field<ULong> groupField, ActivityQueryConfig config) {

        Condition activityCondition =
                this.buildActivityCondition(config.startDate(), config.endDate(), config.stageIds());

        SelectConditionStep<?> query = this.dslContext
                .select(groupField, ENTITY_PROCESSOR_STAGES.NAME, DSL.countDistinct(this.idField))
                .from(this.table)
                .join(ENTITY_PROCESSOR_ACTIVITIES)
                .on(this.idField.eq(ENTITY_PROCESSOR_ACTIVITIES.TICKET_ID))
                .leftJoin(ENTITY_PROCESSOR_STAGES)
                .on(ENTITY_PROCESSOR_ACTIVITIES.STAGE_ID.eq(ENTITY_PROCESSOR_STAGES.ID))
                .where(config.ticketConditions())
                .and(activityCondition);

        return config.requiresGroupFieldNotNull() ? query.and(groupField.isNotNull()) : query;
    }

    private SelectConditionStep<?> buildActivityBasedStageOnlyDateCountSelect(
            Field<LocalDateTime> selectedBucketDateField,
            Field<? extends Number> countField,
            ActivityQueryConfig config) {

        Condition activityCondition =
                this.buildActivityCondition(config.startDate(), config.endDate(), config.stageIds());

        SelectConditionStep<?> query = this.dslContext
                .select(ENTITY_PROCESSOR_STAGES.NAME, selectedBucketDateField, countField)
                .from(this.table)
                .join(ENTITY_PROCESSOR_ACTIVITIES)
                .on(this.idField.eq(ENTITY_PROCESSOR_ACTIVITIES.TICKET_ID))
                .leftJoin(ENTITY_PROCESSOR_STAGES)
                .on(ENTITY_PROCESSOR_ACTIVITIES.STAGE_ID.eq(ENTITY_PROCESSOR_STAGES.ID))
                .where(config.ticketConditions())
                .and(activityCondition);

        return this.applyTicketNotNullFilters(
                query, config.requiresCreatedByNotNull(), config.requiresClientIdNotNull());
    }

    public Flux<PerDateCount> getTicketPerAssignedUserStageSourceDateCount(
            ProcessorAccess access, TicketBucketFilter ticketBucketFilter) {
        return this.getTicketDateCountByGroupAndJoinActivityBased(
                access, ticketBucketFilter, ENTITY_PROCESSOR_TICKETS.SOURCE, NO_STAGE, false);
    }

    private Flux<PerDateCount> getTicketDateCountByGroupAndJoinActivityBased(
            ProcessorAccess access,
            TicketBucketFilter ticketBucketFilter,
            Field<String> groupField,
            String defaultValue,
            boolean requiresNonNull) {

        TimePeriod timePeriod = ticketBucketFilter.getTimePeriod();
        String timezone = ticketBucketFilter.getTimezone();
        Field<LocalDateTime> activityDateGroupField =
                this.toDateBucketGroupKeyField(timePeriod, ENTITY_PROCESSOR_ACTIVITIES.ACTIVITY_DATE, timezone);
        Field<LocalDateTime> minActivityDateField =
                DSL.min(ENTITY_PROCESSOR_ACTIVITIES.ACTIVITY_DATE).as("groupDate");

        return FlatMapUtil.flatMapFlux(
                () -> this.resolveActivityBasedStageConditions(access, ticketBucketFilter)
                        .flux(),
                conditionsTuple -> {
                    var stageConditions = conditionsTuple.getT1();
                    var totalConditions = conditionsTuple.getT2();
                    var filter = conditionsTuple.getT3();

                    ActivityQueryConfig config = new ActivityQueryConfig(
                            stageConditions,
                            filter.getStartDate(),
                            filter.getEndDate(),
                            filter.getStageIds(),
                            requiresNonNull,
                            false,
                            null);

                    Select<?> select = filter.isIncludeTotal()
                            ? this.unionAsRecord(
                                    this.buildActivityBasedGroupedDateCountSelect(
                                                    groupField, minActivityDateField, config)
                                            .groupBy(groupField, ENTITY_PROCESSOR_STAGES.NAME, activityDateGroupField),
                                    this.buildTotalOnlyDateCountSelect(
                                                    groupField,
                                                    DSL.min(ENTITY_PROCESSOR_TICKETS.CREATED_AT)
                                                            .as("groupDate"),
                                                    totalConditions,
                                                    requiresNonNull)
                                            .groupBy(
                                                    groupField,
                                                    this.toDateBucketGroupKeyField(
                                                            timePeriod, ENTITY_PROCESSOR_TICKETS.CREATED_AT, timezone)))
                            : this.buildActivityBasedGroupedDateCountSelect(groupField, minActivityDateField, config)
                                    .groupBy(groupField, ENTITY_PROCESSOR_STAGES.NAME, activityDateGroupField);

                    return Flux.from(select)
                            .map(rec -> this.mapToPerDateCountDistinct(
                                    rec, groupField, minActivityDateField, defaultValue));
                });
    }

    private SelectConditionStep<?> buildActivityBasedGroupedDateCountSelect(
            Field<String> groupField, Field<LocalDateTime> minDateField, ActivityQueryConfig config) {

        Condition activityCondition =
                this.buildActivityCondition(config.startDate(), config.endDate(), config.stageIds());

        SelectConditionStep<?> query = this.dslContext
                .select(ENTITY_PROCESSOR_STAGES.NAME, groupField, minDateField, DSL.countDistinct(this.idField))
                .from(this.table)
                .join(ENTITY_PROCESSOR_ACTIVITIES)
                .on(this.idField.eq(ENTITY_PROCESSOR_ACTIVITIES.TICKET_ID))
                .leftJoin(ENTITY_PROCESSOR_STAGES)
                .on(ENTITY_PROCESSOR_ACTIVITIES.STAGE_ID.eq(ENTITY_PROCESSOR_STAGES.ID))
                .where(config.ticketConditions())
                .and(activityCondition);

        return config.requiresGroupFieldNotNull() ? query.and(groupField.isNotNull()) : query;
    }

    private PerDateCount mapToPerDateCountDistinct(
            Record rec, Field<String> groupField, Field<LocalDateTime> dateTimeField, String defaultValue) {

        String stageName = rec.get(ENTITY_PROCESSOR_STAGES.NAME);
        if (stageName == null) stageName = defaultValue;

        return new PerDateCount()
                .setDate(rec.get(dateTimeField))
                .setMapValue(stageName)
                .setGroupedValue(rec.get(groupField))
                .setCount(rec.get(DSL.countDistinct(this.idField)).longValue());
    }

    private SelectConditionStep<?> buildTotalOnlyDateCountSelect(
            Field<String> groupField,
            Field<LocalDateTime> groupByDateField,
            Condition conditions,
            boolean requiresNonNull) {

        SelectConditionStep<?> query = this.dslContext
                .select(
                        DSL.val(TOTAL).as(ENTITY_PROCESSOR_STAGES.NAME),
                        groupField,
                        groupByDateField,
                        DSL.count(this.idField))
                .from(this.table)
                .where(conditions);

        return requiresNonNull ? query.and(groupField.isNotNull()) : query;
    }

    public Flux<PerDateCount> getTicketCountPerStageAndDateWithClientId(
            ProcessorAccess access, TicketBucketFilter ticketBucketFilter, TimePeriod timePeriod) {
        return this.getTicketDateCountByStageOnlyActivityBased(
                access, ticketBucketFilter, timePeriod, NO_STAGE, Boolean.FALSE, Boolean.TRUE);
    }

    private Flux<PerDateCount> getTicketDateCountByStageOnlyActivityBased(
            ProcessorAccess access,
            TicketBucketFilter ticketBucketFilter,
            TimePeriod timePeriod,
            String defaultValue,
            boolean requiresCreatedByNotNull,
            Boolean requiresClientIdNotNull) {

        String timezone = ticketBucketFilter.getTimezone();
        Field<LocalDateTime> activityDateGroupField =
                this.toDateBucketGroupKeyField(timePeriod, ENTITY_PROCESSOR_ACTIVITIES.ACTIVITY_DATE, timezone);
        Field<LocalDateTime> ticketDateGroupField =
                this.toDateBucketGroupKeyField(timePeriod, ENTITY_PROCESSOR_TICKETS.CREATED_AT, timezone);
        Field<LocalDateTime> selectedActivityDateField =
                DSL.min(ENTITY_PROCESSOR_ACTIVITIES.ACTIVITY_DATE).as("bucketDate");
        Field<LocalDateTime> selectedTicketDateField =
                DSL.min(ENTITY_PROCESSOR_TICKETS.CREATED_AT).as("bucketDate");
        Field<? extends Number> countField = DSL.countDistinct(this.idField);

        return FlatMapUtil.flatMapFlux(
                () -> this.resolveActivityBasedStageConditions(access, ticketBucketFilter)
                        .flux(),
                conditionsTuple -> {
                    var stageConditions = conditionsTuple.getT1();
                    var totalConditions = conditionsTuple.getT2();
                    var filter = conditionsTuple.getT3();

                    ActivityQueryConfig config = new ActivityQueryConfig(
                            stageConditions,
                            filter.getStartDate(),
                            filter.getEndDate(),
                            filter.getStageIds(),
                            false,
                            requiresCreatedByNotNull,
                            requiresClientIdNotNull);

                    Select<?> select = filter.isIncludeTotal()
                            ? this.unionAsRecord(
                                    this.buildActivityBasedStageOnlyDateCountSelect(
                                                    selectedActivityDateField, countField, config)
                                            .groupBy(activityDateGroupField, ENTITY_PROCESSOR_STAGES.NAME),
                                    this.buildTotalOnlyStageDateCountSelect(
                                                    selectedTicketDateField,
                                                    DSL.count(this.idField),
                                                    totalConditions,
                                                    requiresCreatedByNotNull,
                                                    requiresClientIdNotNull)
                                            .groupBy(ticketDateGroupField))
                            : this.buildActivityBasedStageOnlyDateCountSelect(
                                            selectedActivityDateField, countField, config)
                                    .groupBy(activityDateGroupField, ENTITY_PROCESSOR_STAGES.NAME);

                    return Flux.from(select)
                            .map(rec -> this.mapToPerStageOnlyDateCountDistinct(
                                    rec, selectedActivityDateField, countField, defaultValue));
                });
    }

    private PerDateCount mapToPerStageOnlyDateCountDistinct(
            Record rec, Field<LocalDateTime> dateTimeField, Field<? extends Number> countField, String defaultValue) {

        String stageName = rec.get(ENTITY_PROCESSOR_STAGES.NAME);
        if (stageName == null) stageName = defaultValue;

        return new PerDateCount()
                .setDate(rec.get(dateTimeField))
                .setMapValue(stageName)
                .setGroupedValue(null)
                .setCount(rec.get(countField).longValue());
    }

    private SelectConditionStep<?> buildTotalOnlyStageDateCountSelect(
            Field<LocalDateTime> selectedBucketDateField,
            Field<? extends Number> countField,
            Condition conditions,
            boolean requiresCreatedByNotNull,
            Boolean requiresClientIdNotNull) {

        SelectConditionStep<?> query = this.dslContext
                .select(DSL.val(TOTAL).as(ENTITY_PROCESSOR_STAGES.NAME), selectedBucketDateField, countField)
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

    private Condition buildActivityCondition(LocalDateTime startDate, LocalDateTime endDate, List<ULong> stageIds) {
        Condition condition = ENTITY_PROCESSOR_ACTIVITIES
                .ACTIVITY_ACTION
                .eq(ActivityAction.STAGE_UPDATE)
                .and(ENTITY_PROCESSOR_ACTIVITIES.STAGE_ID.isNotNull());

        condition =
                this.applyDateRangeCondition(condition, ENTITY_PROCESSOR_ACTIVITIES.ACTIVITY_DATE, startDate, endDate);

        if (stageIds != null && !stageIds.isEmpty()) {
            condition = condition.and(ENTITY_PROCESSOR_ACTIVITIES.STAGE_ID.in(stageIds));
        }
        return condition;
    }

    private Condition applyDateRangeCondition(
            Condition condition, Field<LocalDateTime> dateField, LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate != null && endDate != null) {
            return condition.and(dateField.between(startDate, endDate));
        } else if (startDate != null) {
            return condition.and(dateField.greaterOrEqual(startDate));
        } else if (endDate != null) {
            return condition.and(dateField.lessOrEqual(endDate));
        }
        return condition;
    }

    public Flux<PerDateCount> getUniqueCreatedByCountPerStageAndDateWithClientId(
            ProcessorAccess access, TicketBucketFilter ticketBucketFilter, TimePeriod timePeriod) {
        return this.getUniqueClientDateCountByStageOnlyActivityBased(
                access, ticketBucketFilter, timePeriod, NO_STAGE, Boolean.FALSE, Boolean.TRUE);
    }

    private Flux<PerDateCount> getUniqueClientDateCountByStageOnlyActivityBased(
            ProcessorAccess access,
            TicketBucketFilter ticketBucketFilter,
            TimePeriod timePeriod,
            String defaultValue,
            boolean requiresCreatedByNotNull,
            Boolean requiresClientIdNotNull) {

        String timezone = ticketBucketFilter.getTimezone();
        Field<LocalDateTime> activityDateGroupField =
                this.toDateBucketGroupKeyField(timePeriod, ENTITY_PROCESSOR_ACTIVITIES.ACTIVITY_DATE, timezone);
        Field<LocalDateTime> ticketDateGroupField =
                this.toDateBucketGroupKeyField(timePeriod, ENTITY_PROCESSOR_TICKETS.CREATED_AT, timezone);
        Field<LocalDateTime> selectedActivityDateField =
                DSL.min(ENTITY_PROCESSOR_ACTIVITIES.ACTIVITY_DATE).as("bucketDate");
        Field<LocalDateTime> selectedTicketDateField =
                DSL.min(ENTITY_PROCESSOR_TICKETS.CREATED_AT).as("bucketDate");
        Field<? extends Number> uniqueCountField =
                DSL.countDistinct(ENTITY_PROCESSOR_TICKETS.CLIENT_ID).as("uniqueClientCount");

        return FlatMapUtil.flatMapFlux(
                () -> this.resolveActivityBasedStageConditions(access, ticketBucketFilter)
                        .flux(),
                conditionsTuple -> {
                    var stageConditions = conditionsTuple.getT1();
                    var totalConditions = conditionsTuple.getT2();
                    var filter = conditionsTuple.getT3();

                    ActivityQueryConfig config = new ActivityQueryConfig(
                            stageConditions,
                            filter.getStartDate(),
                            filter.getEndDate(),
                            filter.getStageIds(),
                            false,
                            requiresCreatedByNotNull,
                            requiresClientIdNotNull);

                    Select<?> select = filter.isIncludeTotal()
                            ? this.unionAsRecord(
                                    this.buildActivityBasedUniqueClientStageOnlyDateCountSelect(
                                                    selectedActivityDateField, uniqueCountField, config)
                                            .groupBy(activityDateGroupField, ENTITY_PROCESSOR_STAGES.NAME),
                                    this.buildTotalOnlyStageDateCountSelect(
                                                    selectedTicketDateField,
                                                    DSL.countDistinct(ENTITY_PROCESSOR_TICKETS.CLIENT_ID),
                                                    totalConditions,
                                                    requiresCreatedByNotNull,
                                                    requiresClientIdNotNull)
                                            .groupBy(ticketDateGroupField))
                            : this.buildActivityBasedUniqueClientStageOnlyDateCountSelect(
                                            selectedActivityDateField, uniqueCountField, config)
                                    .groupBy(activityDateGroupField, ENTITY_PROCESSOR_STAGES.NAME);

                    return Flux.from(select)
                            .map(rec -> this.mapToPerStageOnlyDateCountDistinct(
                                    rec, selectedActivityDateField, uniqueCountField, defaultValue));
                });
    }

    private SelectConditionStep<?> buildActivityBasedUniqueClientStageOnlyDateCountSelect(
            Field<LocalDateTime> selectedBucketDateField,
            Field<? extends Number> uniqueCountField,
            ActivityQueryConfig config) {

        Condition activityCondition =
                this.buildActivityCondition(config.startDate(), config.endDate(), config.stageIds());

        SelectConditionStep<?> query = this.dslContext
                .select(ENTITY_PROCESSOR_STAGES.NAME, selectedBucketDateField, uniqueCountField)
                .from(this.table)
                .join(ENTITY_PROCESSOR_ACTIVITIES)
                .on(this.idField.eq(ENTITY_PROCESSOR_ACTIVITIES.TICKET_ID))
                .leftJoin(ENTITY_PROCESSOR_STAGES)
                .on(ENTITY_PROCESSOR_ACTIVITIES.STAGE_ID.eq(ENTITY_PROCESSOR_STAGES.ID))
                .where(config.ticketConditions())
                .and(activityCondition);

        return this.applyTicketNotNullFilters(
                query, config.requiresCreatedByNotNull(), config.requiresClientIdNotNull());
    }

    private Field<LocalDateTime> toDateBucketGroupKeyField(
            TimePeriod timePeriod, Field<LocalDateTime> dateTimeField, String timezone) {

        Field<LocalDateTime> effectiveDateField = StringUtil.safeIsBlank(timezone) || "UTC".equalsIgnoreCase(timezone)
                ? dateTimeField
                : DSL.field(
                        "convert_tz({0}, 'UTC', {1})", SQLDataType.LOCALDATETIME, dateTimeField, DSL.inline(timezone));

        if (timePeriod == null)
            return DSL.field("timestamp(cast({0} as date))", SQLDataType.LOCALDATETIME, effectiveDateField);

        return switch (timePeriod) {
            case WEEKS ->
                DSL.field(
                        "timestamp(date_sub(cast({0} as date), interval weekday(cast({0} as date)) day))",
                        SQLDataType.LOCALDATETIME, effectiveDateField);
            case MONTHS ->
                DSL.field(
                        "str_to_date(date_format({0}, '%Y-%m-01 00:00:00'), '%Y-%m-%d %H:%i:%s')",
                        SQLDataType.LOCALDATETIME, effectiveDateField);
            case QUARTERS ->
                DSL.field(
                        "str_to_date(concat(year({0}), '-', lpad(((quarter({0})-1)*3)+1, 2, '0'), '-01 00:00:00'),"
                                + " '%Y-%m-%d %H:%i:%s')",
                        SQLDataType.LOCALDATETIME, effectiveDateField);
            case YEARS ->
                DSL.field(
                        "str_to_date(date_format({0}, '%Y-01-01 00:00:00'), '%Y-%m-%d %H:%i:%s')",
                        SQLDataType.LOCALDATETIME, effectiveDateField);
            default -> DSL.field("timestamp(cast({0} as date))", SQLDataType.LOCALDATETIME, effectiveDateField);
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
        TimePeriod timePeriod = ticketBucketFilter.getTimePeriod();
        String timezone = ticketBucketFilter.getTimezone();
        Field<LocalDateTime> dateGroupField =
                this.toDateBucketGroupKeyField(timePeriod, ENTITY_PROCESSOR_TICKETS.CREATED_AT, timezone);
        Field<LocalDateTime> groupByDateField =
                DSL.min(ENTITY_PROCESSOR_TICKETS.CREATED_AT).as("groupDate");

        return FlatMapUtil.flatMapFlux(
                () -> this.createTicketBucketConditions(access, ticketBucketFilter)
                        .flux(),
                abstractCondition -> super.filter(abstractCondition).flux(),
                (abstractCondition, conditions) -> {
                    SelectConditionStep<?> query = this.dslContext
                            .select(clientIdAsString, groupByDateField, DSL.count(this.idField))
                            .from(this.table)
                            .where(conditions.and(ENTITY_PROCESSOR_TICKETS.CLIENT_ID.isNotNull()));

                    return Flux.from(query.groupBy(clientIdAsString, dateGroupField))
                            .map(rec -> new PerDateCount()
                                    .setDate(rec.get(groupByDateField))
                                    .setGroupedValue(rec.get(clientIdAsString))
                                    .setCount(rec.get(DSL.count(this.idField)).longValue()));
                });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Select<?> unionAsRecord(Select<?> left, Select<?> right) {
        return (left).union((Select) right);
    }
}
