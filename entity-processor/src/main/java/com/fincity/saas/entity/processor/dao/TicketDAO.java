package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS;

import com.fincity.saas.entity.processor.dao.base.BaseProcessorDAO;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProducts;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

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
                        .and(ENTITY_PROCESSOR_TICKETS.DNC.eq(dnc)))
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
        conditions.add(this.clientCodeField.eq(access.getEffectiveClientCode()));

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
                .on(ENTITY_PROCESSOR_TICKETS.PRODUCT_ID.eq(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS.ID));
    }

    @Override
    public SelectJoinStep<Record1<Integer>> applyCountBaseTableJoins(
            SelectJoinStep<Record1<Integer>> query, MultiValueMap<String, String> queryParams) {
        return super.applyCountBaseTableJoins(query, queryParams)
                .join(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS)
                .on(ENTITY_PROCESSOR_TICKETS.PRODUCT_ID.eq(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS.ID));
    }

    @Override
    protected Mono<Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>>> getSelectJointStep() {
        return Mono.just(Tuples.of(
                dslContext
                        .select(Arrays.asList(table.fields()))
                        .select(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS.PRODUCT_TEMPLATE_ID)
                        .from(table)
                        .join(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS)
                        .on(ENTITY_PROCESSOR_TICKETS.PRODUCT_ID.eq(
                                EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS.ID)),
                dslContext.select(DSL.count()).from(table)));
    }
}
