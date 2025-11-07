package com.fincity.saas.entity.processor.dao.rule;

import java.util.List;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.rule.BaseUserDistributionDto;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class BaseUserDistributionDAO<R extends UpdatableRecord<R>, D extends BaseUserDistributionDto<D>>
        extends BaseUpdatableDAO<R, D> {

    protected BaseUserDistributionDAO(Class<D> flowPojoClass, Table<R> flowTable, Field<ULong> flowTableId) {
        super(flowPojoClass, flowTable, flowTableId);
    }

    public Mono<List<D>> getUserDistributions(ProcessorAccess access, ULong ruleId) {

        if (ruleId == null) return Mono.empty();

        return FlatMapUtil.flatMapMono(
                () -> this.getBaseConditions(access, ruleId),
                super::filter,
                (pCondition, jCondition) -> Flux.from(
                                dslContext.selectFrom(this.table).where(jCondition.and(super.isActiveTrue())))
                        .map(rec -> rec.into(this.pojoClass))
                        .collectList());
    }

    private Mono<AbstractCondition> getBaseConditions(
            ProcessorAccess access, ULong ruleId) {
        AbstractCondition ruleCondition = FilterCondition.make(BaseUserDistributionDto.Fields.ruleId, ruleId);

        return super.processorAccessCondition(ruleCondition, access);
    }
}
