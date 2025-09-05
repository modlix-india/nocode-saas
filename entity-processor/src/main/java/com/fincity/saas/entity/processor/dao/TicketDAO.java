package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS;

import java.util.ArrayList;
import java.util.List;

import org.jooq.Condition;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.entity.processor.dao.base.BaseProcessorDAO;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class TicketDAO extends BaseProcessorDAO<EntityProcessorTicketsRecord, Ticket> {

    protected TicketDAO() {
        super(
                Ticket.class,
                ENTITY_PROCESSOR_TICKETS,
                ENTITY_PROCESSOR_TICKETS.ID,
                ENTITY_PROCESSOR_TICKETS.ASSIGNED_USER_ID);
    }

    public Flux<Ticket> getAllClientTicketsByDnc(ULong clientId, Boolean dnc) {
        return Flux.from(dslContext
                        .selectFrom(table)
                        .where(ENTITY_PROCESSOR_TICKETS.CLIENT_ID.eq(clientId))
                        .and(ENTITY_PROCESSOR_TICKETS.DNC.eq((byte) (Boolean.TRUE.equals(dnc) ? 1 : 0))))
                .map(rec -> rec.into(this.pojoClass));
    }

    public Flux<Ticket> getAllOwnerTickets(ULong ownerId) {
        return Flux.from(dslContext.selectFrom(table).where(ENTITY_PROCESSOR_TICKETS.OWNER_ID.eq(ownerId)))
                .map(rec -> rec.into(this.pojoClass));
    }

    public Mono<Ticket> readByNumberAndEmail(
            ProcessorAccess access, ULong productId, Integer dialCode, String number, String email) {
        return Mono.from(this.dslContext
                        .selectFrom(this.table)
                        .where(this.getOwnerIdentifierConditions(access, productId, dialCode, number, email))
                        .orderBy(this.idField.desc())
                        .limit(1))
                .map(e -> e.into(this.pojoClass));
    }

    private List<Condition> getOwnerIdentifierConditions(
            ProcessorAccess access, ULong productId, Integer dialCode, String number, String email) {

        List<Condition> conditions = new ArrayList<>();

        conditions.add(this.appCodeField.eq(access.getAppCode()));
        conditions.add(this.clientCodeField.eq(access.getClientCode()));

        if (access.isOutsideUser()) conditions.add(this.clientCodeField.eq(access.getEffectiveClientCode()));

        List<Condition> phoneEmailConditions = new ArrayList<>();

        if (number != null)
            phoneEmailConditions.add(ENTITY_PROCESSOR_TICKETS
                    .DIAL_CODE
                    .eq(dialCode.shortValue())
                    .and(ENTITY_PROCESSOR_TICKETS.PHONE_NUMBER.eq(number)));

        if (email != null) phoneEmailConditions.add(ENTITY_PROCESSOR_TICKETS.EMAIL.eq(email));

        if (!phoneEmailConditions.isEmpty())
            conditions.add(
                    phoneEmailConditions.size() > 1
                            ? phoneEmailConditions.get(0).or(phoneEmailConditions.get(1))
                            : phoneEmailConditions.getFirst());

        // we always need Active product entities
        conditions.add(super.isActiveTrue());

        if (productId != null) conditions.add(ENTITY_PROCESSOR_TICKETS.PRODUCT_ID.eq(productId));

        return conditions;
    }
}
