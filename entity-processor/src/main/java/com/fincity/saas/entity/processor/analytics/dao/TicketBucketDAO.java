package com.fincity.saas.entity.processor.analytics.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorStages.ENTITY_PROCESSOR_STAGES;
import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.entity.processor.analytics.dao.base.BaseAnalyticsDAO;
import com.fincity.saas.entity.processor.analytics.model.BucketFilter;
import com.fincity.saas.entity.processor.analytics.model.PerValueCount;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.util.Map;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class TicketBucketDAO extends BaseAnalyticsDAO<EntityProcessorTicketsRecord, Ticket> {

    protected TicketBucketDAO() {
        super(Ticket.class, ENTITY_PROCESSOR_TICKETS, ENTITY_PROCESSOR_TICKETS.ID);
    }

    @Override
    protected Map<String, String> getBucketFilterFieldMappings() {
        return Map.of(
                BucketFilter.Fields.userIds, Ticket.Fields.assignedUserId,
                BucketFilter.Fields.sources, Ticket.Fields.source,
                BucketFilter.Fields.subSources, Ticket.Fields.subSource,
                BucketFilter.Fields.productIds, Ticket.Fields.productId,
                BucketFilter.Fields.startDate, AbstractDTO.Fields.createdAt,
                BucketFilter.Fields.endDate, AbstractDTO.Fields.createdAt);
    }

    public Flux<PerValueCount> getTicketPerAssignedUserStageCount(ProcessorAccess access, BucketFilter bucketFilter) {
        return FlatMapUtil.flatMapFlux(
                () -> super.createBucketConditions(access, bucketFilter).flux(),
                abstractCondition -> super.filter(abstractCondition).flux(),
                (abstractCondition, conditions) -> Flux.from(this.dslContext
                                .select(
                                        ENTITY_PROCESSOR_TICKETS.ASSIGNED_USER_ID,
                                        ENTITY_PROCESSOR_STAGES.NAME,
                                        DSL.count(ENTITY_PROCESSOR_TICKETS.ID))
                                .from(ENTITY_PROCESSOR_TICKETS)
                                .leftJoin(ENTITY_PROCESSOR_STAGES)
                                .on(ENTITY_PROCESSOR_TICKETS.STAGE.eq(ENTITY_PROCESSOR_STAGES.ID))
                                .where(conditions)
                                .groupBy(ENTITY_PROCESSOR_TICKETS.ASSIGNED_USER_ID, ENTITY_PROCESSOR_STAGES.NAME))
                        .map(rec -> new PerValueCount(
                                rec.get(ENTITY_PROCESSOR_TICKETS.ASSIGNED_USER_ID),
                                rec.get(ENTITY_PROCESSOR_STAGES.NAME),
                                rec.get(DSL.count(ENTITY_PROCESSOR_TICKETS.ID)).longValue())));
    }

    public Flux<PerValueCount> getTicketPerProjectStageCount(ProcessorAccess access, BucketFilter bucketFilter) {
        return FlatMapUtil.flatMapFlux(
                () -> super.createBucketConditions(access, bucketFilter).flux(),
                abstractCondition -> super.filter(abstractCondition).flux(),
                (abstractCondition, conditions) -> Flux.from(this.dslContext
                                .select(
                                        ENTITY_PROCESSOR_TICKETS.PRODUCT_ID,
                                        ENTITY_PROCESSOR_STAGES.NAME,
                                        DSL.count(ENTITY_PROCESSOR_TICKETS.ID))
                                .from(ENTITY_PROCESSOR_TICKETS)
                                .leftJoin(ENTITY_PROCESSOR_STAGES)
                                .on(ENTITY_PROCESSOR_TICKETS.STAGE.eq(ENTITY_PROCESSOR_STAGES.ID))
                                .where(ENTITY_PROCESSOR_TICKETS.IS_ACTIVE.eq((byte) 1))
                                .groupBy(ENTITY_PROCESSOR_TICKETS.PRODUCT_ID, ENTITY_PROCESSOR_STAGES.NAME))
                        .map(rec -> new PerValueCount(
                                rec.get(ENTITY_PROCESSOR_TICKETS.PRODUCT_ID),
                                rec.get(ENTITY_PROCESSOR_STAGES.NAME),
                                rec.get(DSL.count(ENTITY_PROCESSOR_TICKETS.ID)).longValue())));
    }
}
