package com.fincity.saas.entity.processor.dao.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.dto.AbstractDTO;
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

    public boolean hasNoAccessAssignment() {
        return this.userAccessField == null;
    }

    private String getUserField(ProcessorAccess access) {
        return access.isOutsideUser() ? AbstractDTO.Fields.createdBy : this.jUserAccessField;
    }

    private boolean isEmptyCondition(AbstractCondition condition) {
        return condition == null || condition.isEmpty();
    }

    @Override
    public Mono<AbstractCondition> processorAccessCondition(AbstractCondition condition, ProcessorAccess access) {

        if (hasNoAccessAssignment()) return super.processorAccessCondition(condition, access);

        return FlatMapUtil.flatMapMono(
                () -> this.addUserIds(condition, access),
                uCondition -> this.addClientIds(uCondition, access),
                (uCondition, cCondition) -> super.processorAccessCondition(cCondition, access));
    }

    private Mono<AbstractCondition> addClientIds(AbstractCondition condition, ProcessorAccess access) {

        if (access.isOutsideUser()) return this.handleOutsideUserAccess(condition, access);

        if (!access.isHasBpAccess()) return this.handleNoBpAccess(condition);

        return this.handleHierarchyAccess(condition, access);
    }

    private Mono<AbstractCondition> handleOutsideUserAccess(AbstractCondition condition, ProcessorAccess access) {

        if (isEmptyCondition(condition))
            return Mono.just(FilterCondition.make(
                    BaseProcessorDto.Fields.clientId, access.getUser().getClientId()));

        return Mono.just(ComplexCondition.and(
                condition,
                FilterCondition.make(
                        BaseProcessorDto.Fields.clientId, access.getUser().getClientId())));
    }

    private Mono<AbstractCondition> handleNoBpAccess(AbstractCondition condition) {

        if (isEmptyCondition(condition))
            return Mono.just(new FilterCondition()
                    .setOperator(FilterConditionOperator.IS_NULL)
                    .setField(BaseProcessorDto.Fields.clientId));

        return FlatMapUtil.flatMapMono(
                () -> condition.removeConditionWithField(BaseProcessorDto.Fields.clientId),
                cond -> Mono.just(ComplexCondition.and(
                        cond,
                        new FilterCondition()
                                .setOperator(FilterConditionOperator.IS_NULL)
                                .setField(BaseProcessorDto.Fields.clientId))));
    }

    private Mono<AbstractCondition> handleHierarchyAccess(AbstractCondition condition, ProcessorAccess access) {
        List<ULong> managingClientIds = access.getUserInherit().getManagingClientIds();

        if (isEmptyCondition(condition))
            return Mono.just(this.createInCondition(BaseProcessorDto.Fields.clientId, managingClientIds));

        return this.processExistingClientConditions(condition, access, managingClientIds);
    }

    private Mono<AbstractCondition> processExistingClientConditions(
            AbstractCondition condition, ProcessorAccess access, List<ULong> managingClientIds) {

        return FlatMapUtil.flatMapMono(
                () -> condition
                        .findConditionWithField(BaseProcessorDto.Fields.clientId)
                        .collectList(),
                clientConditions -> {
                    List<FilterCondition> updatedClientConditions =
                            updateClientConditions(clientConditions, managingClientIds);
                    return this.processUserConditions(condition, this.getUserField(access), updatedClientConditions);
                });
    }

    private Mono<AbstractCondition> processUserConditions(
            AbstractCondition condition, String userField, List<FilterCondition> clientConditions) {

        return FlatMapUtil.flatMapMono(
                () -> condition.findConditionWithField(userField).collectList(),
                userConditions -> this.removeExistingConditionsAndCombine(
                        condition, userField, clientConditions, userConditions));
    }

    private Mono<AbstractCondition> removeExistingConditionsAndCombine(
            AbstractCondition condition,
            String userField,
            List<FilterCondition> clientConditions,
            List<FilterCondition> userConditions) {

        return FlatMapUtil.flatMapMonoWithNull(
                () -> condition.removeConditionWithField(BaseProcessorDto.Fields.clientId),
                conditionWithoutClient -> conditionWithoutClient != null
                        ? conditionWithoutClient.removeConditionWithField(userField)
                        : Mono.empty(),
                (conditionWithoutClient, finalCondition) -> {
                    ComplexCondition or = ComplexCondition.or(
                            ComplexCondition.and(clientConditions.toArray(new FilterCondition[0])),
                            ComplexCondition.and(userConditions.toArray(new FilterCondition[0])));

                    if (this.isEmptyCondition(finalCondition)) return Mono.just(or);

                    return Mono.just(ComplexCondition.and(finalCondition, or));
                });
    }

    private List<FilterCondition> updateClientConditions(
            List<FilterCondition> clientConditions, List<ULong> managingClientIds) {
        if (clientConditions.isEmpty())
            return List.of(createInCondition(BaseProcessorDto.Fields.clientId, managingClientIds));

        clientConditions.forEach(
                fc -> fc.setMultiValue(FilterUtil.intersectLists(fc.getMultiValue(), managingClientIds)));

        return clientConditions;
    }

    private Mono<AbstractCondition> addUserIds(AbstractCondition condition, ProcessorAccess access) {

        String userField = this.getUserField(access);

        List<ULong> userHierarchy = access.getUserInherit().getSubOrg();

        if (isEmptyCondition(condition)) return Mono.just(createInCondition(userField, userHierarchy));

        return this.updateExistingUserCondition(condition, userField, userHierarchy, access)
                .switchIfEmpty(Mono.just(ComplexCondition.and(condition, createInCondition(userField, userHierarchy))));
    }

    private Mono<AbstractCondition> updateExistingUserCondition(
            AbstractCondition condition, String userField, List<?> userHierarchy, ProcessorAccess access) {

        return this.updateExistingCondition(
                condition,
                userField,
                userHierarchy,
                ULongUtil.valueOf(access.getUser().getId()));
    }

    private Mono<AbstractCondition> updateExistingCondition(
            AbstractCondition root, String field, List<?> hierarchy, Object equalsValue) {

        return FlatMapUtil.flatMapMono(() -> root.findConditionWithField(field).collectList(), existingConditions -> {
            if (existingConditions.isEmpty()) return Mono.empty();
            this.updateFilterConditions(existingConditions, hierarchy, equalsValue);
            return Mono.just(root);
        });
    }

    private void updateFilterConditions(List<FilterCondition> conditions, List<?> hierarchy, Object equalsValue) {
        conditions.forEach(fc -> {
            if (fc.getOperator() == FilterConditionOperator.IN)
                fc.setMultiValue(FilterUtil.intersectLists(fc.getMultiValue(), hierarchy));
            if (fc.getOperator() == FilterConditionOperator.EQUALS) fc.setValue(equalsValue);
        });
    }

    private FilterCondition createInCondition(String field, List<?> values) {
        return new FilterCondition()
                .setField(field)
                .setOperator(FilterConditionOperator.IN)
                .setMultiValue(values);
    }

    @SuppressWarnings("unchecked")
    public Flux<D> updateAll(Flux<D> entities) {
        return entities.flatMap(super::update);
    }
}
