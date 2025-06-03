package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS;

import com.fincity.saas.entity.processor.dao.base.BaseProcessorDAO;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class TicketDAO extends BaseProcessorDAO<EntityProcessorTicketsRecord, Ticket> {

    protected TicketDAO() {
        super(Ticket.class, ENTITY_PROCESSOR_TICKETS, ENTITY_PROCESSOR_TICKETS.ID);
    }

    public Flux<Ticket> getAllOwnerTickets(ULong ownerId) {
        return Flux.from(dslContext.selectFrom(table).where(ENTITY_PROCESSOR_TICKETS.OWNER_ID.eq(ownerId)))
                .map(rec -> rec.into(pojoClass));
    }
}
