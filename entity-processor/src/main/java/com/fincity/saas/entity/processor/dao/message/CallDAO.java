package com.fincity.saas.entity.processor.dao.message;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorCalls.ENTITY_PROCESSOR_CALLS;

import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowUpdatableDTO;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.message.Call;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorCallsRecord;
import java.util.List;
import java.util.Map;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class CallDAO extends BaseUpdatableDAO<EntityProcessorCallsRecord, Call> {

    /**
     * What the eager read expands. Matches the fields the old message-service query asked for, so
     * the page's {@code Parent.createdBy.firstName} binding keeps resolving.
     */
    private static final List<String> EAGER_USER_FIELDS = List.of("userId", "firstName", "lastName");

    protected CallDAO() {
        super(Call.class, ENTITY_PROCESSOR_CALLS, ENTITY_PROCESSOR_CALLS.ID);
    }

    /**
     * The call log for a set of deals the caller can see, newest first.
     *
     * <p>Takes an already-resolved deal set rather than a single ticket, matching the WhatsApp
     * thread read: a customer holding several deals sees one call history, because that is what
     * actually happened on the phone.
     *
     * <p>An empty set returns an empty page rather than everything. "No visible deals" must never
     * widen into "no filter", which is precisely the bug that made the message service's version of
     * this endpoint unsafe.
     */
    public Mono<Page<Call>> readCalls(String appCode, String clientCode, List<ULong> ticketIds, Pageable pageable) {

        if (ticketIds == null || ticketIds.isEmpty()) return Mono.just(Page.empty(pageable));

        Condition where = tenant(appCode, clientCode)
                .and(ENTITY_PROCESSOR_CALLS.TICKET_ID.in(ticketIds))
                .and(this.isActiveTrue());

        return Mono.zip(
                        Mono.from(this.dslContext.selectCount().from(this.table).where(where))
                                .map(rec -> rec.value1().longValue())
                                .defaultIfEmpty(0L),
                        Flux.from(this.dslContext
                                        .selectFrom(this.table)
                                        .where(where)
                                        .orderBy(orderKey().desc())
                                        .limit(pageable.getPageSize())
                                        .offset((int) pageable.getOffset()))
                                .map(rec -> rec.into(this.pojoClass))
                                .collectList())
                .map(tup -> new PageImpl<>(tup.getT2(), pageable, tup.getT1()));
    }

    /**
     * The same call log, with {@code createdBy} expanded into a user.
     *
     * <p>Exists because the endpoint this replaces was the eager variant, and the deal profile binds
     * {@code Parent.createdBy.firstName} to show who placed a call. Returning bare ids would leave
     * that field blank, which is a visible regression rather than a cosmetic one: on an outbound
     * call it is the only thing identifying the agent.
     *
     * <p>{@code createdBy} needs no registration here; {@link
     * com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto} already maps it to the user
     * resolver for every entity.
     */
    public Mono<Page<Map<String, Object>>> readCallsEager(
            String appCode, String clientCode, List<ULong> ticketIds, Pageable pageable) {

        if (ticketIds == null || ticketIds.isEmpty()) return Mono.just(Page.empty(pageable));

        AbstractCondition condition = ComplexCondition.and(
                // From the base DTO, not Call.Fields: @FieldNameConstants only generates constants
                // for fields a class declares itself, so the inherited tenant fields are not there.
                FilterCondition.make(AbstractFlowUpdatableDTO.Fields.appCode, appCode)
                        .setOperator(FilterConditionOperator.EQUALS),
                FilterCondition.make(AbstractFlowUpdatableDTO.Fields.clientCode, clientCode)
                        .setOperator(FilterConditionOperator.EQUALS),
                new FilterCondition()
                        .setField(Call.Fields.ticketId)
                        .setOperator(FilterConditionOperator.IN)
                        .setMultiValue(ticketIds));

        return this.readPageFilterEager(pageable, condition, EAGER_USER_FIELDS, null, null);
    }

    /** The row a provider event refers to, if we have already seen this call. */
    public Mono<Call> readByProviderCallId(String appCode, String clientCode, String providerCallId) {

        if (providerCallId == null || providerCallId.isBlank()) return Mono.empty();

        return Mono.from(this.dslContext
                        .selectFrom(this.table)
                        .where(tenant(appCode, clientCode))
                        .and(ENTITY_PROCESSOR_CALLS.PROVIDER_CALL_ID.eq(providerCallId)))
                .map(rec -> rec.into(this.pojoClass));
    }

    /**
     * Deals with at least one call, so a caller can tell a deal that has been rung from one that has
     * merely been created.
     */
    public Mono<List<ULong>> ticketsWithCalls(String appCode, String clientCode, List<ULong> ticketIds) {

        if (ticketIds == null || ticketIds.isEmpty()) return Mono.just(List.of());

        return Flux.from(this.dslContext
                        .selectDistinct(ENTITY_PROCESSOR_CALLS.TICKET_ID)
                        .from(this.table)
                        .where(tenant(appCode, clientCode))
                        .and(ENTITY_PROCESSOR_CALLS.TICKET_ID.in(ticketIds))
                        .and(this.isActiveTrue()))
                .map(rec -> rec.value1())
                .collectList();
    }

    /**
     * Historic calls for a customer number that were never filed against a deal.
     *
     * <p>Used only by the backfill, which matches by number because that is the sole signal old
     * rows carry. Deliberately not used at read time: matching a live call to a deal by number is
     * exactly the heuristic this table exists to replace.
     */
    public Mono<Integer> attachTicketByCustomerNumber(
            String appCode, String clientCode, String customerPhoneNumber, ULong ticketId) {

        if (customerPhoneNumber == null || customerPhoneNumber.isBlank() || ticketId == null)
            return Mono.just(0);

        return Mono.from(this.dslContext
                .update(ENTITY_PROCESSOR_CALLS)
                .set(ENTITY_PROCESSOR_CALLS.TICKET_ID, ticketId)
                .where(tenant(appCode, clientCode))
                .and(ENTITY_PROCESSOR_CALLS.CUSTOMER_PHONE_NUMBER.eq(customerPhoneNumber))
                .and(ENTITY_PROCESSOR_CALLS.TICKET_ID.isNull()));
    }

    /**
     * The most recent call time per deal, for the conversation list's ordering.
     *
     * <p>Batched over the visible page rather than denormalised onto the ticket, following the
     * enrichment already used for the latest comment and task due date.
     */
    public Mono<List<org.jooq.Record2<ULong, java.time.LocalDateTime>>> lastCallTimes(
            String appCode, String clientCode, List<ULong> ticketIds) {

        if (ticketIds == null || ticketIds.isEmpty()) return Mono.just(List.of());

        return Flux.from(this.dslContext
                        .select(ENTITY_PROCESSOR_CALLS.TICKET_ID, DSL.max(orderKey()))
                        .from(this.table)
                        .where(tenant(appCode, clientCode))
                        .and(ENTITY_PROCESSOR_CALLS.TICKET_ID.in(ticketIds))
                        .and(this.isActiveTrue())
                        .groupBy(ENTITY_PROCESSOR_CALLS.TICKET_ID))
                .map(rec -> (org.jooq.Record2<ULong, java.time.LocalDateTime>) rec)
                .collectList();
    }

    /**
     * When the call happened, for ordering.
     *
     * <p>{@code START_TIME} is the truth but arrives with the provider's callback, so a row created
     * the moment the call was placed can briefly have none. Falling back to {@code CREATED_AT} keeps
     * a just-placed call at the top of the log instead of at the bottom.
     */
    private Field<java.time.LocalDateTime> orderKey() {
        return DSL.coalesce(ENTITY_PROCESSOR_CALLS.START_TIME, ENTITY_PROCESSOR_CALLS.CREATED_AT);
    }

    private Condition tenant(String appCode, String clientCode) {
        return ENTITY_PROCESSOR_CALLS
                .APP_CODE
                .eq(appCode)
                .and(ENTITY_PROCESSOR_CALLS.CLIENT_CODE.eq(clientCode));
    }
}
