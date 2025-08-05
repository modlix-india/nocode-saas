package com.fincity.saas.entity.processor.analytics.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS;

import org.springframework.stereotype.Component;

import com.fincity.saas.entity.processor.analytics.dao.base.BaseAnalyticsDAO;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;

@Component
public class TicketBucketDAO extends BaseAnalyticsDAO<EntityProcessorTicketsRecord, Ticket> {

    protected TicketBucketDAO() {
        super(Ticket.class, ENTITY_PROCESSOR_TICKETS, ENTITY_PROCESSOR_TICKETS.ID);
    }
}
