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
import com.fincity.saas.entity.processor.analytics.model.PerDateCount;
import com.fincity.saas.entity.processor.analytics.model.PerValueCount;
import com.fincity.saas.entity.processor.analytics.model.TicketBucketFilter;
import com.fincity.saas.entity.processor.analytics.model.base.BaseFilter;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class TicketBucketDAO extends BaseAnalyticsDAO<EntityProcessorTicketsRecord, Ticket> {

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
                .setMatchOperator(FilterConditionOperator.IS_TRUE));
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
        return FlatMapUtil.flatMapFlux(
                () -> this.createTicketBucketConditions(access, ticketBucketFilter)
                        .flux(),
                abstractCondition -> super.filter(abstractCondition).flux(),
                (abstractCondition, conditions) -> {
                    Condition condition = conditions;

                    return Flux.from(this.dslContext
                                    .select(
                                            ENTITY_PROCESSOR_TICKETS.ASSIGNED_USER_ID,
                                            ENTITY_PROCESSOR_STAGES.NAME,
                                            DSL.count(ENTITY_PROCESSOR_TICKETS.ID))
                                    .from(ENTITY_PROCESSOR_TICKETS)
                                    .leftJoin(ENTITY_PROCESSOR_STAGES)
                                    .on(ENTITY_PROCESSOR_TICKETS.STAGE.eq(ENTITY_PROCESSOR_STAGES.ID))
                                    .where(conditions)
                                    .groupBy(ENTITY_PROCESSOR_TICKETS.ASSIGNED_USER_ID, ENTITY_PROCESSOR_STAGES.NAME))
                            .map(rec -> {
                                String stageName = rec.get(ENTITY_PROCESSOR_STAGES.NAME);
                                if (stageName == null) stageName = "No Stage";
                                return new PerValueCount()
                                        .setGroupedId(rec.get(ENTITY_PROCESSOR_TICKETS.ASSIGNED_USER_ID))
                                        .setMapValue(stageName)
                                        .setCount(rec.get(DSL.count(ENTITY_PROCESSOR_TICKETS.ID))
                                                .longValue());
                            });
                });
    }

    public Flux<PerDateCount> getTicketPerAssignedUserStageSourceDateCount(
            ProcessorAccess access, TicketBucketFilter ticketBucketFilter) {
        return FlatMapUtil.flatMapFlux(
                () -> this.createTicketBucketConditions(access, ticketBucketFilter)
                        .flux(),
                abstractCondition -> super.filter(abstractCondition).flux(),
                (abstractCondition, conditions) -> {
                    Condition condition = conditions;

                    return Flux.from(this.dslContext
                                    .select(
                                            ENTITY_PROCESSOR_STAGES.NAME,
                                            ENTITY_PROCESSOR_TICKETS.SOURCE,
                                            DSL.cast(ENTITY_PROCESSOR_TICKETS.CREATED_AT, SQLDataType.LOCALDATE),
                                            DSL.count(ENTITY_PROCESSOR_TICKETS.ID))
                                    .from(ENTITY_PROCESSOR_TICKETS)
                                    .leftJoin(ENTITY_PROCESSOR_STAGES)
                                    .on(ENTITY_PROCESSOR_TICKETS.STAGE.eq(ENTITY_PROCESSOR_STAGES.ID))
                                    .where(conditions)
                                    .groupBy(
                                            ENTITY_PROCESSOR_TICKETS.SOURCE,
                                            ENTITY_PROCESSOR_STAGES.NAME,
                                            DSL.cast(ENTITY_PROCESSOR_TICKETS.CREATED_AT, SQLDataType.LOCALDATE)))
                            .map(rec -> {
                                String stageName = rec.get(ENTITY_PROCESSOR_STAGES.NAME);
                                if (stageName == null) stageName = "No Stage";
                                return new PerDateCount()
                                        .setDate(rec.get(
                                                DSL.cast(ENTITY_PROCESSOR_TICKETS.CREATED_AT, SQLDataType.LOCALDATE)))
                                        .setMapValue(stageName)
                                        .setGroupedValue(rec.get(ENTITY_PROCESSOR_TICKETS.SOURCE))
                                        .setCount(rec.get(DSL.count(ENTITY_PROCESSOR_TICKETS.ID))
                                                .longValue());
                            });
                });
    }

    public Flux<PerValueCount> getTicketPerCreatedByStageCount(
            ProcessorAccess access, TicketBucketFilter ticketBucketFilter) {
        return FlatMapUtil.flatMapFlux(
                () -> this.createTicketBucketConditions(access, ticketBucketFilter)
                        .flux(),
                abstractCondition -> super.filter(abstractCondition).flux(),
                (abstractCondition, conditions) -> Flux.from(this.dslContext
                                .select(
                                        ENTITY_PROCESSOR_TICKETS.CREATED_BY,
                                        ENTITY_PROCESSOR_STAGES.NAME,
                                        DSL.count(ENTITY_PROCESSOR_TICKETS.ID))
                                .from(this.table)
                                .leftJoin(ENTITY_PROCESSOR_STAGES)
                                .on(ENTITY_PROCESSOR_TICKETS.STAGE.eq(ENTITY_PROCESSOR_STAGES.ID))
                                .where(conditions)
                                .and(ENTITY_PROCESSOR_TICKETS.CREATED_BY.isNotNull())
                                .groupBy(ENTITY_PROCESSOR_TICKETS.CREATED_BY, ENTITY_PROCESSOR_STAGES.NAME))
                        .map(rec -> {
                            String stageName = rec.get(ENTITY_PROCESSOR_STAGES.NAME);
                            if (stageName == null) stageName = "No Stage";
                            return new PerValueCount()
                                    .setGroupedId(rec.get(ENTITY_PROCESSOR_TICKETS.CREATED_BY))
                                    .setMapValue(stageName)
                                    .setCount(rec.get(DSL.count(ENTITY_PROCESSOR_TICKETS.ID))
                                            .longValue());
                        }));
    }

    public Flux<PerValueCount> getTicketPerClientIdStageCount(
            ProcessorAccess access, TicketBucketFilter ticketBucketFilter) {
        return FlatMapUtil.flatMapFlux(
                () -> this.createTicketBucketConditions(access, ticketBucketFilter)
                        .flux(),
                abstractCondition -> super.filter(abstractCondition).flux(),
                (abstractCondition, conditions) -> Flux.from(this.dslContext
                                .select(
                                        ENTITY_PROCESSOR_TICKETS.CLIENT_ID,
                                        ENTITY_PROCESSOR_STAGES.NAME,
                                        DSL.count(ENTITY_PROCESSOR_TICKETS.ID))
                                .from(this.table)
                                .leftJoin(ENTITY_PROCESSOR_STAGES)
                                .on(ENTITY_PROCESSOR_TICKETS.STAGE.eq(ENTITY_PROCESSOR_STAGES.ID))
                                .where(conditions)
                                .and(ENTITY_PROCESSOR_TICKETS.CLIENT_ID.isNotNull())
                                .groupBy(ENTITY_PROCESSOR_TICKETS.CLIENT_ID, ENTITY_PROCESSOR_STAGES.NAME))
                        .map(rec -> {
                            String stageName = rec.get(ENTITY_PROCESSOR_STAGES.NAME);
                            if (stageName == null) stageName = "No Stage";
                            return new PerValueCount()
                                    .setGroupedId(rec.get(ENTITY_PROCESSOR_TICKETS.CLIENT_ID))
                                    .setMapValue(stageName)
                                    .setCount(rec.get(DSL.count(ENTITY_PROCESSOR_TICKETS.ID))
                                            .longValue());
                        }));
    }

    public Flux<PerValueCount> getTicketPerProjectStageCount(
            ProcessorAccess access, TicketBucketFilter ticketBucketFilter) {
        return FlatMapUtil.flatMapFlux(
                () -> this.createTicketBucketConditions(access, ticketBucketFilter)
                        .flux(),
                abstractCondition -> super.filter(abstractCondition).flux(),
                (abstractCondition, conditions) -> Flux.from(this.dslContext
                                .select(
                                        ENTITY_PROCESSOR_TICKETS.PRODUCT_ID,
                                        ENTITY_PROCESSOR_STAGES.NAME,
                                        DSL.count(ENTITY_PROCESSOR_TICKETS.ID))
                                .from(this.table)
                                .leftJoin(ENTITY_PROCESSOR_STAGES)
                                .on(ENTITY_PROCESSOR_TICKETS.STAGE.eq(ENTITY_PROCESSOR_STAGES.ID))
                                .where(conditions)
                                .groupBy(ENTITY_PROCESSOR_TICKETS.PRODUCT_ID, ENTITY_PROCESSOR_STAGES.NAME))
                        .map(rec -> {
                            String stageName = rec.get(ENTITY_PROCESSOR_STAGES.NAME);
                            if (stageName == null) stageName = "No Stage";
                            return new PerValueCount()
                                    .setGroupedId(rec.get(ENTITY_PROCESSOR_TICKETS.PRODUCT_ID))
                                    .setMapValue(stageName)
                                    .setCount(rec.get(DSL.count(ENTITY_PROCESSOR_TICKETS.ID))
                                            .longValue());
                        }));
    }
}
