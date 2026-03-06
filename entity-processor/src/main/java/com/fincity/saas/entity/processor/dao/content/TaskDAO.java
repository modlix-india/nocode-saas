package com.fincity.saas.entity.processor.dao.content;

import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_TASKS;
import static com.fincity.saas.entity.processor.jooq.Tables.ENTITY_PROCESSOR_TICKETS;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.entity.processor.dao.content.base.BaseContentDAO;
import com.fincity.saas.entity.processor.dto.content.Task;
import com.fincity.saas.entity.processor.dto.content.base.BaseContentDto;
import com.fincity.saas.entity.processor.enums.content.ContentEntitySeries;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTasksRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class TaskDAO extends BaseContentDAO<EntityProcessorTasksRecord, Task> {

    protected TaskDAO() {
        super(Task.class, ENTITY_PROCESSOR_TASKS, ENTITY_PROCESSOR_TASKS.ID);
    }

    @Override
    public Mono<AbstractCondition> processorAccessCondition(AbstractCondition condition, ProcessorAccess access) {

        if (access.getUser() == null && access.getUserInherit() == null)
            return Mono.just(super.addAppCodeAndClientCode(condition, access));

        if (access.isOutsideUser()) return super.processorAccessCondition(condition, access);

        return FlatMapUtil.flatMapMono(
                () -> this.readAccessibleTicketIds(access),
                ticketIds -> {
                    AbstractCondition accessCondition = this.buildTaskAccessCondition(ticketIds, access);

                    AbstractCondition finalCondition = condition == null || condition.isEmpty()
                            ? accessCondition
                            : ComplexCondition.and(condition, accessCondition);

                    return Mono.just(super.addAppCodeAndClientCode(finalCondition, access));
                });
    }

    private AbstractCondition buildTaskAccessCondition(List<ULong> ticketIds, ProcessorAccess access) {

        List<ULong> subOrg = access.getUserInherit().getSubOrg();

        // Non-ticket tasks (OWNER/USER scoped): access by CREATED_BY
        AbstractCondition nonTicketBranch = ComplexCondition.and(
                new FilterCondition()
                        .setField(BaseContentDto.Fields.contentEntitySeries)
                        .setOperator(FilterConditionOperator.IN)
                        .setMultiValue(List.of(ContentEntitySeries.OWNER, ContentEntitySeries.USER)),
                new FilterCondition()
                        .setField(AbstractDTO.Fields.createdBy)
                        .setOperator(FilterConditionOperator.IN)
                        .setMultiValue(subOrg));

        if (ticketIds.isEmpty()) return nonTicketBranch;

        // TICKET-scoped tasks: access by ticket's assignedUserId
        AbstractCondition ticketBranch = ComplexCondition.and(
                FilterCondition.make(BaseContentDto.Fields.contentEntitySeries, ContentEntitySeries.TICKET),
                new FilterCondition()
                        .setField(BaseContentDto.Fields.ticketId)
                        .setOperator(FilterConditionOperator.IN)
                        .setMultiValue(ticketIds));

        return ComplexCondition.or(ticketBranch, nonTicketBranch);
    }

    private Mono<List<ULong>> readAccessibleTicketIds(ProcessorAccess access) {

        List<ULong> subOrg = access.getUserInherit().getSubOrg();

        return Flux.from(dslContext
                        .selectDistinct(ENTITY_PROCESSOR_TASKS.TICKET_ID)
                        .from(ENTITY_PROCESSOR_TASKS)
                        .join(ENTITY_PROCESSOR_TICKETS)
                        .on(ENTITY_PROCESSOR_TASKS.TICKET_ID.eq(ENTITY_PROCESSOR_TICKETS.ID))
                        .where(ENTITY_PROCESSOR_TASKS.CONTENT_ENTITY_SERIES.eq(ContentEntitySeries.TICKET))
                        .and(ENTITY_PROCESSOR_TICKETS.ASSIGNED_USER_ID.in(subOrg))
                        .and(ENTITY_PROCESSOR_TASKS.APP_CODE.eq(access.getAppCode()))
                        .and(ENTITY_PROCESSOR_TASKS.CLIENT_CODE.eq(access.getEffectiveClientCode())))
                .map(rec -> rec.get(ENTITY_PROCESSOR_TASKS.TICKET_ID))
                .collectList();
    }
}
