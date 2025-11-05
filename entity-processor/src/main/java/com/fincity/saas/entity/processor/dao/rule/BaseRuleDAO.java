package com.fincity.saas.entity.processor.dao.rule;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.rule.BaseRuleDto;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.util.List;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class BaseRuleDAO<R extends UpdatableRecord<R>, D extends BaseRuleDto<D>>
        extends BaseUpdatableDAO<R, D> {

    protected final String jEntityIdField;

    protected BaseRuleDAO(Class<D> flowPojoClass, Table<R> flowTable, Field<ULong> flowTableId, String jEntityIdField) {
        super(flowPojoClass, flowTable, flowTableId);
        this.jEntityIdField = jEntityIdField;
    }

    public Mono<List<D>> getRules(ProcessorAccess access, ULong entityId, List<ULong> stageIds, boolean getDefault) {
        return FlatMapUtil.flatMapMono(
                () -> this.getBaseConditionsWithDefault(access, entityId, stageIds, getDefault),
                super::filter,
                (pCondition, jCondition) -> Flux.from(
                                dslContext.selectFrom(this.table).where(jCondition.and(super.isActiveTrue())))
                        .map(rec -> rec.into(this.pojoClass))
                        .collectList());
    }

    public Mono<List<D>> getRules(ProcessorAccess access, ULong entityId, ULong stageId) {
        return FlatMapUtil.flatMapMono(
                () -> this.getStageCondition(access, entityId, stageId),
                super::filter,
                (pCondition, jCondition) -> Flux.from(
                                dslContext.selectFrom(this.table).where(jCondition.and(super.isActiveTrue())))
                        .map(rec -> rec.into(this.pojoClass))
                        .collectList());
    }

    public Mono<D> getRule(ProcessorAccess access, ULong entityId, Integer order) {
        return FlatMapUtil.flatMapMono(
                () -> this.getOrderCondition(access, entityId, order),
                super::filter,
                (pCondition, jCondition) -> Mono.from(
                                dslContext.selectFrom(this.table).where(jCondition.and(super.isActiveTrue())))
                        .map(rec -> rec.into(this.pojoClass)));
    }

    private Mono<AbstractCondition> getBaseConditionsWithDefault(
            ProcessorAccess access, ULong entityId, List<ULong> stageIds, boolean getDefault) {

        FilterCondition entityCondition = FilterCondition.make(this.jEntityIdField, entityId);

        if (stageIds != null && !stageIds.isEmpty())
            return super.processorAccessCondition(
                    ComplexCondition.and(this.getStageConditionWithDefault(stageIds, getDefault), entityCondition),
                    access);

        if (getDefault)
            return super.processorAccessCondition(ComplexCondition.and(getDefaultCondition(), entityCondition), access);

        return super.processorAccessCondition(entityCondition, access);
    }

    private AbstractCondition getStageConditionWithDefault(List<ULong> stageIds, boolean includeDefault) {
        AbstractCondition stageCondition = new FilterCondition()
                .setField(BaseRuleDto.Fields.stageId)
                .setMatchOperator(FilterConditionOperator.IN)
                .setMultiValue(stageIds);

        return includeDefault ? ComplexCondition.or(stageCondition, getDefaultCondition()) : stageCondition;
    }

    private AbstractCondition getDefaultCondition() {
        return new FilterCondition()
                .setField(BaseRuleDto.Fields.isDefault)
                .setOperator(FilterConditionOperator.IS_TRUE);
    }

    private Mono<AbstractCondition> getOrderCondition(ProcessorAccess access, ULong entityId, Integer order) {

        if (order == null) return Mono.empty();

        return super.processorAccessCondition(
                ComplexCondition.and(
                        FilterCondition.make(this.jEntityIdField, entityId),
                        FilterCondition.make(BaseRuleDto.Fields.order, order)),
                access);
    }

    private Mono<AbstractCondition> getStageCondition(ProcessorAccess access, ULong entityId, ULong stageId) {

        if (stageId == null) return Mono.empty();

        return super.processorAccessCondition(
                ComplexCondition.and(
                        FilterCondition.make(this.jEntityIdField, entityId),
                        FilterCondition.make(BaseRuleDto.Fields.stageId, stageId)),
                access);
    }
}
