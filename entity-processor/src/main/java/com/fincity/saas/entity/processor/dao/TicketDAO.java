package com.fincity.saas.entity.processor.dao;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.entity.processor.dao.base.BaseProcessorDAO;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorProducts;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTicketsRecord;
import com.fincity.saas.entity.processor.model.common.Email;
import com.fincity.saas.entity.processor.model.common.PhoneNumber;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.service.product.ProductTicketRuRuleService;
import com.fincity.saas.entity.processor.service.rule.TicketPeDuplicationRuleService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Component
public class TicketDAO extends BaseProcessorDAO<EntityProcessorTicketsRecord, Ticket> {

    private ProductTicketRuRuleService productTicketRuRuleService;
    private TicketPeDuplicationRuleService ticketPeDuplicationRuleService;

    protected TicketDAO() {
        super(
                Ticket.class,
                ENTITY_PROCESSOR_TICKETS,
                ENTITY_PROCESSOR_TICKETS.ID,
                ENTITY_PROCESSOR_TICKETS.ASSIGNED_USER_ID);
    }

    @Lazy
    @Autowired
    private void setProductTicketRuRuleService(ProductTicketRuRuleService productTicketRuRuleService) {
        this.productTicketRuRuleService = productTicketRuRuleService;
    }

    @Lazy
    @Autowired
    private void setTicketPeDuplicationRuleService(TicketPeDuplicationRuleService ticketPeDuplicationRuleService) {
        this.ticketPeDuplicationRuleService = ticketPeDuplicationRuleService;
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

    public Mono<Ticket> readTicketByNumberAndEmail(
            AbstractCondition condition,
            ProcessorAccess access,
            ULong productId,
            PhoneNumber phoneNumber,
            Email email) {

        return FlatMapUtil.flatMapMono(
                () -> this.getOwnerIdentifierConditions(condition, access, productId, phoneNumber, email)
                        .map(ownerIdentifierConditions ->
                                super.addAppCodeAndClientCode(ownerIdentifierConditions, access)),
                super::filter,
                (pCondition, jCondition) -> Mono.from(this.dslContext
                                .selectFrom(this.table)
                                .where(jCondition.and(super.isActiveTrue()))
                                .orderBy(this.updatedByField.desc())
                                .limit(1))
                        .map(e -> e.into(this.pojoClass)));
    }

    public Mono<List<Ticket>> readTicketsByNumberAndEmail(
            AbstractCondition condition,
            ProcessorAccess access,
            ULong productId,
            PhoneNumber phoneNumber,
            Email email) {

        return FlatMapUtil.flatMapMono(
                () -> this.getOwnerIdentifierConditions(condition, access, productId, phoneNumber, email)
                        .map(ownerIdentifierConditions ->
                                super.addAppCodeAndClientCode(ownerIdentifierConditions, access)),
                super::filter,
                (pCondition, jCondition) -> Flux.from(
                                this.dslContext.selectFrom(this.table).where(jCondition.and(super.isActiveTrue())))
                        .map(e -> e.into(this.pojoClass))
                        .collectList());
    }

    private Mono<AbstractCondition> getOwnerIdentifierConditions(
            AbstractCondition condition,
            ProcessorAccess access,
            ULong productId,
            PhoneNumber phoneNumber,
            Email email) {

        return FlatMapUtil.flatMapMono(
                () -> this.ticketPeDuplicationRuleService.getTicketCondition(access, phoneNumber, email),
                pECondition -> {
                    List<AbstractCondition> conditions = new ArrayList<>();

                    if (condition != null && condition.isNonEmpty()) conditions.add(condition);

                    if (pECondition != null && pECondition.isNonEmpty()) conditions.add(pECondition);

                    if (productId != null) conditions.add(FilterCondition.make(Ticket.Fields.productId, productId));

                    if (conditions.isEmpty()) return Mono.empty();

                    return Mono.just(ComplexCondition.and(conditions));
                });
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
                dslContext
                        .select(DSL.count())
                        .from(table)
                        .join(EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS)
                        .on(ENTITY_PROCESSOR_TICKETS.PRODUCT_ID.eq(
                                EntityProcessorProducts.ENTITY_PROCESSOR_PRODUCTS.ID))));
    }

    @Override
    public Mono<AbstractCondition> processorAccessCondition(AbstractCondition condition, ProcessorAccess access) {
        return FlatMapUtil.flatMapMono(
                        () -> this.productTicketRuRuleService
                                .getUserReadConditions(access)
                                .map(rCondition ->
                                        condition != null ? ComplexCondition.and(condition, rCondition) : rCondition),
                        rCondition -> Mono.just(super.addAppCodeAndClientCode(rCondition, access)),
                        (rCondition, readCondition) -> super.processorAccessCondition(condition, access),
                        (rCondition, readCondition, baseCondition) -> readCondition == null
                                ? Mono.just(baseCondition)
                                : Mono.just(ComplexCondition.or(baseCondition, readCondition)))
                .switchIfEmpty(super.processorAccessCondition(condition, access));
    }
}
