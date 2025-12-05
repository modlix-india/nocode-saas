package com.fincity.saas.entity.processor.dao.rule;

import java.util.ArrayList;
import java.util.List;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.rule.BaseRuleDto;
import com.fincity.saas.entity.processor.dto.rule.BaseUserDistributionDto;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class BaseRuleDAO<
                R extends UpdatableRecord<R>, U extends BaseUserDistributionDto<U>, D extends BaseRuleDto<U, D>>
        extends BaseUpdatableDAO<R, D> {

    private static final String ORDER = "ORDER";

    protected final Field<Integer> orderField;

    protected BaseRuleDAO(Class<D> flowPojoClass, Table<R> flowTable, Field<ULong> flowTableId) {
        super(flowPojoClass, flowTable, flowTableId);
        this.orderField = flowTable.field(ORDER, Integer.class);
    }

    public Mono<List<D>> getRules(
            AbstractCondition condition, ProcessorAccess access, ULong productId, ULong productTemplateId) {
        return FlatMapUtil.flatMapMono(
                () -> this.getBaseConditions(condition, access, productId, productTemplateId, null),
                super::filter,
                (pCondition, jCondition) -> Flux.from(
                                dslContext.selectFrom(this.table).where(jCondition.and(super.isActiveTrue())))
                        .map(rec -> rec.into(this.pojoClass))
                        .collectList());
    }

    public Mono<D> getRule(
            AbstractCondition condition,
            ProcessorAccess access,
            ULong productId,
            ULong productTemplateId,
            Integer order) {
        return FlatMapUtil.flatMapMono(
                () -> this.getBaseConditions(condition, access, productId, productTemplateId, order),
                super::filter,
                (pCondition, jCondition) -> Mono.from(
                                dslContext.selectFrom(this.table).where(jCondition.and(super.isActiveTrue())))
                        .map(rec -> rec.into(this.pojoClass)));
    }

    private Mono<AbstractCondition> getBaseConditions(
            AbstractCondition condition,
            ProcessorAccess access,
            ULong productId,
            ULong productTemplateId,
            Integer order) {

        List<AbstractCondition> conditions = new ArrayList<>(5);

        if (condition != null) conditions.add(condition);

        AbstractCondition productCondition = this.buildProductCondition(productId, productTemplateId);
        if (productCondition != null) conditions.add(productCondition);

        if (order != null) {
            conditions.add(FilterCondition.make(BaseRuleDto.Fields.order, order));
            return super.processorAccessCondition(ComplexCondition.and(conditions), access);
        }

        AbstractCondition baseConditions = ComplexCondition.and(conditions);

        AbstractCondition defaultRuleCondition = createDefaultRuleCondition(productId, productTemplateId);

        AbstractCondition finalCondition = baseConditions != null
                ? ComplexCondition.or(defaultRuleCondition, baseConditions)
                : defaultRuleCondition;

        return super.processorAccessCondition(finalCondition, access);
    }

    protected AbstractCondition buildProductCondition(ULong productId, ULong productTemplateId) {

        List<AbstractCondition> productConditions = new ArrayList<>(2);

        if (productId != null) productConditions.add(FilterCondition.make(BaseRuleDto.Fields.productId, productId));

        if (productTemplateId != null) {
            if (productId != null)
                productConditions.add(FilterCondition.make(BaseRuleDto.Fields.productTemplateId, productTemplateId));
            else
                productConditions.add(ComplexCondition.and(
                        FilterCondition.make(BaseRuleDto.Fields.productTemplateId, productTemplateId),
                        new FilterCondition()
                                .setField(BaseRuleDto.Fields.productId)
                                .setOperator(FilterConditionOperator.IS_NULL)));
        }

        return productConditions.isEmpty() ? null : ComplexCondition.and(productConditions);
    }

    private AbstractCondition createDefaultRuleCondition(ULong productId, ULong productTemplateId) {

        List<AbstractCondition> defaultConditions = new ArrayList<>(3);

        defaultConditions.add(FilterCondition.make(BaseRuleDto.Fields.order, BaseRuleDto.DEFAULT_ORDER));

        if (productId != null) defaultConditions.add(FilterCondition.make(BaseRuleDto.Fields.productId, productId));

        if (productTemplateId != null)
            defaultConditions.add(FilterCondition.make(BaseRuleDto.Fields.productTemplateId, productTemplateId));

        return ComplexCondition.and(defaultConditions);
    }

    public Mono<List<D>> decrementOrdersAfter(
            ProcessorAccess access, ULong productId, ULong productTemplateId, Integer deletedOrder) {
        return FlatMapUtil.flatMapMono(
                () -> this.getBaseConditions(null, access, productId, productTemplateId, null),
                super::filter,
                (pCondition, jCondition) -> {
                    Condition updateCondition =
                            jCondition.and(super.isActiveTrue()).and(this.orderField.gt(deletedOrder));
                    return Mono.from(dslContext
                                    .update(this.table)
                                    .set(this.orderField, this.orderField.minus(1))
                                    .where(updateCondition))
                            .then(Flux.from(dslContext
                                            .selectFrom(this.table)
                                            .where(jCondition
                                                    .and(super.isActiveTrue())
                                                    .and(this.orderField.ge(deletedOrder - 1))))
                                    .map(rec -> rec.into(this.pojoClass))
                                    .collectList());
                });
    }
}
