package com.fincity.saas.entity.processor.dao.content;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_TASKS;
import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_TICKETS;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.entity.processor.dao.content.base.BaseContentDAO;
import com.fincity.saas.entity.processor.dto.content.Task;
import com.fincity.saas.entity.processor.enums.content.ContentEntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTasksRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.util.List;
import org.jooq.Condition;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Select;
import org.jooq.SelectJoinStep;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class TaskDAO extends BaseContentDAO<EntityProcessorTasksRecord, Task> {

    private static class RawJooqCondition extends AbstractCondition {

        private static final long serialVersionUID = 1L;

        private final transient Condition jooqCondition;

        RawJooqCondition(Condition jooqCondition) {
            this.jooqCondition = jooqCondition;
        }

        Condition getJooqCondition() {
            return jooqCondition;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public Flux<FilterCondition> findConditionWithField(String fieldName) {
            return Flux.empty();
        }

        @Override
        public Flux<FilterCondition> findConditionWithPrefix(String prefix) {
            return Flux.empty();
        }

        @Override
        public Flux<FilterCondition> findAndTrimPrefix(String prefix) {
            return Flux.empty();
        }

        @Override
        public Flux<FilterCondition> findAndCreatePrefix(String prefix) {
            return Flux.empty();
        }

        @Override
        public Mono<AbstractCondition> removeConditionWithField(String fieldName) {
            return Mono.just(this);
        }
    }

    protected TaskDAO() {
        super(Task.class, ENTITY_PROCESSOR_TASKS, ENTITY_PROCESSOR_TASKS.ID);
    }

    @Override
    public Mono<AbstractCondition> processorAccessCondition(AbstractCondition condition, ProcessorAccess access) {

        if (access.getUser() == null && access.getUserInherit() == null)
            return Mono.just(super.addAppCodeAndClientCode(condition, access));

        if (access.isOutsideUser()) return super.processorAccessCondition(condition, access);

        AbstractCondition accessCondition = new RawJooqCondition(buildTaskAccessConditionJooq(access));

        AbstractCondition finalCondition = condition == null || condition.isEmpty()
                ? accessCondition
                : ComplexCondition.and(condition, accessCondition);

        return Mono.just(super.addAppCodeAndClientCode(finalCondition, access));
    }

    @Override
    public Mono<Condition> filter(AbstractCondition condition, SelectJoinStep<Record> selectJoinStep) {
        if (condition instanceof RawJooqCondition rjc) return Mono.just(rjc.getJooqCondition());

        return super.filter(condition, selectJoinStep);
    }

    private Condition buildTaskAccessConditionJooq(ProcessorAccess access) {

        List<ULong> subOrg = access.getUserInherit().getSubOrg();

        // Non-ticket tasks (OWNER/USER scoped): access by CREATED_BY
        Condition nonTicketBranch = ENTITY_PROCESSOR_TASKS
                .CONTENT_ENTITY_SERIES
                .in(ContentEntitySeries.OWNER, ContentEntitySeries.USER)
                .and(ENTITY_PROCESSOR_TASKS.CREATED_BY.in(subOrg));

        // TICKET-scoped tasks: access by ticket's assignedUserId via subquery
        Condition ticketBranch = ENTITY_PROCESSOR_TASKS
                .CONTENT_ENTITY_SERIES
                .eq(ContentEntitySeries.TICKET)
                .and(ENTITY_PROCESSOR_TASKS.TICKET_ID.in(accessibleTicketIdsSubquery(access)));

        return ticketBranch.or(nonTicketBranch);
    }

    private Select<Record1<ULong>> accessibleTicketIdsSubquery(ProcessorAccess access) {

        List<ULong> subOrg = access.getUserInherit().getSubOrg();

        return dslContext
                .selectDistinct(ENTITY_PROCESSOR_TASKS.TICKET_ID)
                .from(ENTITY_PROCESSOR_TASKS)
                .join(ENTITY_PROCESSOR_TICKETS)
                .on(ENTITY_PROCESSOR_TASKS.TICKET_ID.eq(ENTITY_PROCESSOR_TICKETS.ID))
                .where(ENTITY_PROCESSOR_TASKS.CONTENT_ENTITY_SERIES.eq(ContentEntitySeries.TICKET))
                .and(ENTITY_PROCESSOR_TICKETS.ASSIGNED_USER_ID.in(subOrg))
                .and(ENTITY_PROCESSOR_TASKS.APP_CODE.eq(access.getAppCode()))
                .and(ENTITY_PROCESSOR_TASKS.CLIENT_CODE.eq(access.getEffectiveClientCode()));
    }
}
