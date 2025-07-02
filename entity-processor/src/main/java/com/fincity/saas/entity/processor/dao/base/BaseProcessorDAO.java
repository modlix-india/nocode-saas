package com.fincity.saas.entity.processor.dao.base;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.util.EagerUtil;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class BaseProcessorDAO<R extends UpdatableRecord<R>, D extends BaseProcessorDto<D>>
        extends BaseUpdatableDAO<R, D> {

    protected final Field<ULong> userAccessField;
    protected final String jUserAccessField;

    protected BaseProcessorDAO(
            Class<D> flowPojoClass, Table<R> flowTable, Field<ULong> flowTableId, Field<ULong> userAccessField) {
        super(flowPojoClass, flowTable, flowTableId);
        this.userAccessField = userAccessField;
        this.jUserAccessField = EagerUtil.fromJooqField(userAccessField.getName());
    }

    protected BaseProcessorDAO(Class<D> flowPojoClass, Table<R> flowTable, Field<ULong> flowTableId) {
        super(flowPojoClass, flowTable, flowTableId);
        this.userAccessField = null;
        this.jUserAccessField = null;
    }

    public boolean hasAccessAssignment() {
        return this.userAccessField != null;
    }

    @Override
    public Mono<AbstractCondition> processorAccessCondition(AbstractCondition condition, ProcessorAccess access) {
        return this.addUserIds(condition, access)
                .flatMap(uCondition -> super.processorAccessCondition(uCondition, access));
    }

    private Mono<AbstractCondition> addUserIds(AbstractCondition condition, ProcessorAccess access) {

        if (!hasAccessAssignment()) return Mono.just(condition);

        if (condition == null || condition.isEmpty())
            return Mono.just(new FilterCondition()
                    .setField(this.jUserAccessField)
                    .setOperator(FilterConditionOperator.IN)
                    .setMultiValue(access.getSubOrg())
            );

        return Mono.just(ComplexCondition.and(
                condition,
                new FilterCondition()
                        .setField(this.jUserAccessField)
                        .setOperator(FilterConditionOperator.IN)
                        .setMultiValue(access.getSubOrg())));
    }

    public Flux<D> updateAll(Flux<D> entities) {
        return entities.flatMap(super::update);
    }
}
