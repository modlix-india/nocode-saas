package com.fincity.saas.entity.processor.dao.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.entity.processor.constant.BusinessPartnerConstant;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.util.EagerUtil;
import com.fincity.saas.entity.processor.util.FilterUtil;
import java.util.List;
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
        return FlatMapUtil.flatMapMonoWithNull(
                () -> this.addUserIds(condition, access),
                uCondition -> this.addClientIds(uCondition, access),
                (uCondition, cCondition) -> super.processorAccessCondition(cCondition, access));
    }

    private Mono<AbstractCondition> addClientIds(AbstractCondition condition, ProcessorAccess access) {
        if (!BusinessPartnerConstant.isBpManager(access.getUser().getAuthorities())) {
            if (this.isEmptyCondition(condition)) return Mono.empty();
            return condition.removeConditionWithField(BaseProcessorDto.Fields.clientId);
        }

        if (this.isEmptyCondition(condition))
            return this.buildInCondition(BaseProcessorDto.Fields.clientId, access.getUserInherit().getClientHierarchy());

        return this.updateExistingCondition(
                        condition,
                        BaseProcessorDto.Fields.clientId,
                        access.getUserInherit().getClientHierarchy(),
                        ULongUtil.valueOf(access.getUser().getClientId()))
                .switchIfEmpty(this.appendNewCondition(
                        condition, BaseProcessorDto.Fields.clientId, access.getUserInherit().getClientHierarchy()));
    }

    private Mono<AbstractCondition> addUserIds(AbstractCondition condition, ProcessorAccess access) {
        if (!hasAccessAssignment()) return Mono.just(condition);

        if (isEmptyCondition(condition)) return this.buildInCondition(this.jUserAccessField, access.getUserInherit().getSubOrg());

        return this.updateExistingCondition(
                        condition,
                        this.jUserAccessField,
                        access.getUserInherit().getSubOrg(),
                        ULongUtil.valueOf(access.getUser().getId()))
                .switchIfEmpty(this.appendNewCondition(condition, this.jUserAccessField, access.getUserInherit().getSubOrg()));
    }

    private boolean isEmptyCondition(AbstractCondition condition) {
        return condition == null || condition.isEmpty();
    }

    private Mono<AbstractCondition> buildInCondition(String field, List<?> values) {
        return Mono.just(new FilterCondition()
                .setField(field)
                .setOperator(FilterConditionOperator.IN)
                .setMultiValue(values));
    }

    private Mono<AbstractCondition> updateExistingCondition(
            AbstractCondition root, String field, List<?> hierarchy, Object equalsValue) {
        return FlatMapUtil.flatMapMono(() -> root.findConditionWithField(field).collectList(), existingConditions -> {
            for (FilterCondition fc : existingConditions) {
                if (fc.getOperator() == FilterConditionOperator.IN)
                    fc.setMultiValue(FilterUtil.intersectLists(fc.getMultiValue(), hierarchy));
                if (fc.getOperator() == FilterConditionOperator.EQUALS) fc.setValue(equalsValue);
            }
            return Mono.just(root);
        });
    }

    private Mono<AbstractCondition> appendNewCondition(AbstractCondition root, String field, List<?> values) {
        return buildInCondition(field, values).flatMap(fc -> Mono.just(ComplexCondition.and(root, fc)));
    }

    public Flux<D> updateAll(Flux<D> entities) {
        return entities.flatMap(super::update);
    }
}
