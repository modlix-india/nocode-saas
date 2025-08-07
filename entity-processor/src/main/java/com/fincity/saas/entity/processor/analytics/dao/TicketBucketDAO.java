package com.fincity.saas.entity.processor.analytics.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorStages.ENTITY_PROCESSOR_STAGES;
import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS;

import com.fincity.saas.entity.processor.analytics.dao.base.BaseAnalyticsDAO;
import com.fincity.saas.entity.processor.analytics.model.BucketFilter;
import com.fincity.saas.entity.processor.analytics.model.PerValueCount;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class TicketBucketDAO extends BaseAnalyticsDAO<EntityProcessorTicketsRecord, Ticket> {

    protected TicketBucketDAO() {
        super(Ticket.class, ENTITY_PROCESSOR_TICKETS, ENTITY_PROCESSOR_TICKETS.ID);
    }

    public Flux<PerValueCount> getTicketPerAssignedUserStageCount(
            ProcessorAccess processorAccess, BucketFilter bucketFilter) {

        return Flux.from(this.dslContext
                        .select(
                                ENTITY_PROCESSOR_TICKETS.ASSIGNED_USER_ID,
                                ENTITY_PROCESSOR_STAGES.NAME,
                                DSL.count(ENTITY_PROCESSOR_TICKETS.ID))
                        .from(ENTITY_PROCESSOR_TICKETS)
                        .leftJoin(ENTITY_PROCESSOR_STAGES)
                        .on(ENTITY_PROCESSOR_TICKETS.STAGE.eq(ENTITY_PROCESSOR_STAGES.ID))
                        .where(ENTITY_PROCESSOR_TICKETS.IS_ACTIVE.eq((byte) 1))
                        .groupBy(ENTITY_PROCESSOR_TICKETS.ASSIGNED_USER_ID, ENTITY_PROCESSOR_STAGES.NAME))
                .map(rec -> new PerValueCount(
                        rec.get(ENTITY_PROCESSOR_TICKETS.ASSIGNED_USER_ID),
                        rec.get(ENTITY_PROCESSOR_STAGES.NAME),
                        rec.get(DSL.count(ENTITY_PROCESSOR_TICKETS.ID)).longValue()));
    }

    public Flux<PerValueCount> getTicketPerCreatedUserStageCount(
            ProcessorAccess processorAccess, BucketFilter bucketFilter) {
        return Flux.from(this.dslContext
                        .select(
                                ENTITY_PROCESSOR_TICKETS.CREATED_BY,
                                ENTITY_PROCESSOR_STAGES.NAME,
                                DSL.count(ENTITY_PROCESSOR_TICKETS.ID))
                        .from(ENTITY_PROCESSOR_TICKETS)
                        .leftJoin(ENTITY_PROCESSOR_STAGES)
                        .on(ENTITY_PROCESSOR_TICKETS.STAGE.eq(ENTITY_PROCESSOR_STAGES.ID))
                        .where(ENTITY_PROCESSOR_TICKETS.IS_ACTIVE.eq((byte) 1))
                        .groupBy(ENTITY_PROCESSOR_TICKETS.CREATED_BY, ENTITY_PROCESSOR_STAGES.NAME))
                .map(rec -> new PerValueCount(
                        rec.get(ENTITY_PROCESSOR_TICKETS.CREATED_BY),
                        rec.get(ENTITY_PROCESSOR_STAGES.NAME),
                        rec.get(DSL.count(ENTITY_PROCESSOR_TICKETS.ID)).longValue()));
    }

    public Flux<PerValueCount> getTicketPerProjectStageCount(
            ProcessorAccess processorAccess, BucketFilter bucketFilter) {
        return Flux.from(this.dslContext
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
                        rec.get(DSL.count(ENTITY_PROCESSOR_TICKETS.ID)).longValue()));
    }
}
