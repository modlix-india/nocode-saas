package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS;

import com.fincity.saas.entity.processor.dao.base.BaseProcessorDAO;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import java.util.ArrayList;
import java.util.List;
import org.jooq.Condition;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class TicketDAO extends BaseProcessorDAO<EntityProcessorTicketsRecord, Ticket> {

    protected TicketDAO() {
        super(Ticket.class, ENTITY_PROCESSOR_TICKETS, ENTITY_PROCESSOR_TICKETS.ID);
    }

    public Flux<Ticket> getAllOwnerTickets(ULong ownerId) {
        return Flux.from(dslContext.selectFrom(table).where(ENTITY_PROCESSOR_TICKETS.OWNER_ID.eq(ownerId)))
                .map(rec -> rec.into(this.pojoClass));
    }

    public Mono<Ticket> readByNumberAndEmail(
            String appCode, String clientCode, ULong productId, Integer dialCode, String number, String email) {
        return Mono.from(this.dslContext
                        .selectFrom(this.table)
                        .where(this.getOwnerIdentifierConditions(
                                appCode, clientCode, productId, dialCode, number, email))
                        .orderBy(this.idField.desc())
                        .limit(1))
                .map(e -> e.into(this.pojoClass));
    }

    private List<Condition> getOwnerIdentifierConditions(
            String appCode, String clientCode, ULong productId, Integer dialCode, String number, String email) {

        List<Condition> conditions = new ArrayList<>();

        conditions.add(this.appCodeField.eq(appCode));
        conditions.add(this.clientCodeField.eq(clientCode));

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
