package com.fincity.saas.entity.processor.dao.rule;

import java.util.List;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.entity.processor.dao.base.BaseUpdatableDAO;
import com.fincity.saas.entity.processor.dto.rule.BaseRuleDto;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class BaseRuleDAO<R extends UpdatableRecord<R>, D extends BaseRuleDto<D>>
        extends BaseUpdatableDAO<R, D> {

    protected BaseRuleDAO(Class<D> flowPojoClass, Table<R> flowTable, Field<ULong> flowTableId) {
        super(flowPojoClass, flowTable, flowTableId);
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
			AbstractCondition condition, ProcessorAccess access, ULong productId, ULong productTemplateId, Integer order) {
		return FlatMapUtil.flatMapMono(
				() -> this.getBaseConditions(condition, access, productId, productTemplateId, order),
				super::filter,
				(pCondition, jCondition) -> Mono.from(
								dslContext.selectFrom(this.table).where(jCondition.and(super.isActiveTrue())))
						.map(rec -> rec.into(this.pojoClass)));
	}

    private Mono<AbstractCondition> getBaseConditions(
            AbstractCondition condition, ProcessorAccess access, ULong productId, ULong productTemplateId, Integer order) {
        AbstractCondition productCondition = FilterCondition.make(BaseRuleDto.Fields.productId, productId);
        AbstractCondition productTemplateCondition =
                FilterCondition.make(BaseRuleDto.Fields.productTemplateId, productTemplateId);

		AbstractCondition orderCondition = FilterCondition.make(BaseRuleDto.Fields.order, order);

        if (condition == null) {
            return super.processorAccessCondition(
                    ComplexCondition.and(productCondition, productTemplateCondition, orderCondition), access);
        } else {
            return super.processorAccessCondition(
                    ComplexCondition.and(condition, productCondition, productTemplateCondition, orderCondition), access);
        }
    }
}
