package com.fincity.saas.entity.processor.dao.base;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.eager.EagerUtil;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectJoinStep;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

public abstract class BaseProcessorDAO<R extends UpdatableRecord<R>, D extends BaseProcessorDto<D>>
        extends BaseUpdatableDAO<R, D> implements ITimezoneDAO<R, D> {

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

        if (condition == null)
            return FlatMapUtil.flatMapMono(
                    () -> this.addUserIds(null, access),
                    uCondition -> this.processUserAndClientConditions(uCondition, access));

        String userField = this.getUserField(access);

        return FlatMapUtil.flatMapMono(
                () -> condition.findConditionWithField(userField).collectList(),
                existingUserConditions -> FlatMapUtil.flatMapMono(
                        () -> condition
                                .findConditionWithField(BaseProcessorDto.Fields.clientId)
                                .collectList(),
                        existingClientConditions -> this.processUserConditionBasedOnFilters(
                                condition, existingUserConditions, existingClientConditions, access)));
    }

    private Mono<AbstractCondition> processUserConditionBasedOnFilters(
            AbstractCondition originalCondition,
            List<FilterCondition> existingUserConditions,
            List<FilterCondition> existingClientConditions,
            ProcessorAccess access) {

        if (!existingUserConditions.isEmpty() && !existingClientConditions.isEmpty())
            return FlatMapUtil.flatMapMono(
                    () -> this.addUserIds(originalCondition, access),
                    uCondition -> this.processBothUserAndClientConditions(originalCondition, uCondition, access));

        if (!existingUserConditions.isEmpty())
            return FlatMapUtil.flatMapMono(
                    () -> this.addUserIds(originalCondition, access),
                    uCondition -> this.processOnlyUserCondition(uCondition, access));

        if (!existingClientConditions.isEmpty())
            return FlatMapUtil.flatMapMono(
                    () -> this.addClientIds(originalCondition, access),
                    cCondition -> super.processorAccessCondition(cCondition, access));

        return FlatMapUtil.flatMapMono(
                () -> this.addUserIds(originalCondition, access),
                uCondition -> this.processUserAndClientConditions(uCondition, access));
    }

    private Mono<AbstractCondition> processOnlyUserCondition(
            AbstractCondition userProcessedCondition, ProcessorAccess access) {

        return FlatMapUtil.flatMapMono(
                        () -> userProcessedCondition.removeConditionWithField(BaseProcessorDto.Fields.clientId),
                        conditionWithoutClient -> super.processorAccessCondition(
                                conditionWithoutClient != null ? conditionWithoutClient : userProcessedCondition,
                                access))
                .switchIfEmpty(super.processorAccessCondition(userProcessedCondition, access));
    }

    private Mono<AbstractCondition> processUserAndClientConditions(
            AbstractCondition userProcessedCondition, ProcessorAccess access) {

        return FlatMapUtil.flatMapMono(
                () -> this.addClientIds(userProcessedCondition, access),
                cCondition -> super.processorAccessCondition(cCondition, access));
    }

    private Mono<AbstractCondition> processBothUserAndClientConditions(
            AbstractCondition originalCondition, AbstractCondition userProcessedCondition, ProcessorAccess access) {

        return FlatMapUtil.flatMapMono(
                () -> originalCondition
                        .findConditionWithField(BaseProcessorDto.Fields.clientId)
                        .collectList(),
                clientConditions ->
                        this.processClientConditionsForBothFilters(userProcessedCondition, clientConditions, access));
    }

    private Mono<AbstractCondition> processClientConditionsForBothFilters(
            AbstractCondition userProcessedCondition, List<FilterCondition> clientConditions, ProcessorAccess access) {

        if (access.isOutsideUser()) return this.handleBothFiltersForOutsideUser(userProcessedCondition, access);

        if (!access.isHasBpAccess()) return super.processorAccessCondition(userProcessedCondition, access);

        return this.handleBothFiltersForBpAccess(userProcessedCondition, clientConditions, access);
    }

    private Mono<AbstractCondition> handleBothFiltersForOutsideUser(
            AbstractCondition userProcessedCondition, ProcessorAccess access) {

        FilterCondition clientIdCondition = FilterCondition.make(
                BaseProcessorDto.Fields.clientId, access.getUser().getClientId());
        return super.processorAccessCondition(ComplexCondition.and(userProcessedCondition, clientIdCondition), access);
    }

    private Mono<AbstractCondition> handleBothFiltersForBpAccess(
            AbstractCondition userProcessedCondition, List<FilterCondition> clientConditions, ProcessorAccess access) {

        List<ULong> managingClientIds = access.getUserInherit().getManagingClientIds();
        AbstractCondition clientCondition = this.updateClientConditions(clientConditions, managingClientIds);

        return FlatMapUtil.flatMapMono(
                        () -> userProcessedCondition.removeConditionWithField(BaseProcessorDto.Fields.clientId),
                        conditionWithoutClient -> {
                            AbstractCondition combined = ComplexCondition.and(
                                    conditionWithoutClient != null && !this.isEmptyCondition(conditionWithoutClient)
                                            ? conditionWithoutClient
                                            : userProcessedCondition,
                                    clientCondition);
                            return super.processorAccessCondition(combined, access);
                        })
                .switchIfEmpty(super.processorAccessCondition(
                        ComplexCondition.and(userProcessedCondition, clientCondition), access));
    }

    private Mono<AbstractCondition> addClientIds(AbstractCondition condition, ProcessorAccess access) {

        if (access.isOutsideUser()) return this.handleOutsideUserAccess(condition, access);

        if (!access.isHasBpAccess()) return this.handleNoBpAccess(condition);

        return this.handleHierarchyAccess(condition, access);
    }

    private Mono<AbstractCondition> handleOutsideUserAccess(AbstractCondition condition, ProcessorAccess access) {

        FilterCondition clientIdCondition = FilterCondition.make(
                BaseProcessorDto.Fields.clientId, access.getUser().getClientId());

        if (isEmptyCondition(condition)) return Mono.just(clientIdCondition);

        return Mono.just(ComplexCondition.and(condition, clientIdCondition));
    }

    private Mono<AbstractCondition> handleNoBpAccess(AbstractCondition condition) {

        if (isEmptyCondition(condition)) return Mono.just(condition);

        return FlatMapUtil.flatMapMono(
                () -> condition.removeConditionWithField(BaseProcessorDto.Fields.clientId), Mono::just);
    }

    private Mono<AbstractCondition> handleHierarchyAccess(AbstractCondition condition, ProcessorAccess access) {
        List<ULong> managingClientIds = access.getUserInherit().getManagingClientIds();

        if (isEmptyCondition(condition))
            return Mono.just(new FilterCondition()
                    .setField(BaseProcessorDto.Fields.clientId)
                    .setOperator(FilterConditionOperator.IN)
                    .setMultiValue(managingClientIds));

        return this.processExistingClientConditions(condition, access, managingClientIds);
    }

    private Mono<AbstractCondition> processExistingClientConditions(
            AbstractCondition condition, ProcessorAccess access, List<ULong> managingClientIds) {

        return FlatMapUtil.flatMapMono(
                () -> condition
                        .findConditionWithField(BaseProcessorDto.Fields.clientId)
                        .collectList(),
                clientConditions -> {
                    AbstractCondition updatedClientCondition =
                            updateClientConditions(clientConditions, managingClientIds);
                    return this.processUserConditions(
                            condition, this.getUserField(access), updatedClientCondition, access);
                });
    }

    private Mono<AbstractCondition> processUserConditions(
            AbstractCondition condition, String userField, AbstractCondition clientCondition, ProcessorAccess access) {

        return FlatMapUtil.flatMapMono(
                () -> condition.findConditionWithField(userField).collectList(), userConditions -> {
                    List<ULong> userHierarchy = access.getUserInherit().getSubOrg();
                    AbstractCondition updatedUserCondition =
                            this.updateUserConditions(userConditions, userField, userHierarchy);
                    return this.removeExistingConditionsAndCombine(
                            condition, userField, clientCondition, updatedUserCondition);
                });
    }

    private Mono<AbstractCondition> removeExistingConditionsAndCombine(
            AbstractCondition condition,
            String userField,
            AbstractCondition clientCondition,
            AbstractCondition userCondition) {

        return FlatMapUtil.flatMapMonoWithNull(
                () -> condition.removeConditionWithField(BaseProcessorDto.Fields.clientId),
                conditionWithoutClient -> conditionWithoutClient != null
                        ? conditionWithoutClient.removeConditionWithField(userField)
                        : Mono.empty(),
                (conditionWithoutClient, finalCondition) -> {
                    ComplexCondition or = ComplexCondition.or(clientCondition, userCondition);

                    return Mono.just(
                            this.isEmptyCondition(finalCondition) ? or : ComplexCondition.and(finalCondition, or));
                });
    }

    private AbstractCondition updateClientConditions(
            List<FilterCondition> clientConditions, List<ULong> managingClientIds) {
        return this.updateConditionsWithHierarchy(
                clientConditions, BaseProcessorDto.Fields.clientId, managingClientIds);
    }

    private Mono<AbstractCondition> addUserIds(AbstractCondition condition, ProcessorAccess access) {

        String userField = this.getUserField(access);

        List<ULong> userHierarchy = access.getUserInherit().getSubOrg();

        if (isEmptyCondition(condition))
            return Mono.just(new FilterCondition()
                    .setField(userField)
                    .setOperator(FilterConditionOperator.IN)
                    .setMultiValue(userHierarchy));

        return this.processExistingUserConditions(condition, userField, userHierarchy);
    }

    private Mono<AbstractCondition> processExistingUserConditions(
            AbstractCondition condition, String userField, List<ULong> userHierarchy) {

        return FlatMapUtil.flatMapMono(
                () -> condition.findConditionWithField(userField).collectList(), userConditions -> {
                    AbstractCondition userCondition =
                            this.updateUserConditions(userConditions, userField, userHierarchy);

                    return FlatMapUtil.flatMapMono(
                                    () -> condition.removeConditionWithField(userField),
                                    conditionWithoutUser -> Mono.just(
                                            conditionWithoutUser != null && !this.isEmptyCondition(conditionWithoutUser)
                                                    ? ComplexCondition.and(conditionWithoutUser, userCondition)
                                                    : userCondition))
                            .switchIfEmpty(Mono.just(userCondition));
                });
    }

    private AbstractCondition updateUserConditions(
            List<FilterCondition> userConditions, String userField, List<ULong> userHierarchy) {
        return this.updateConditionsWithHierarchy(userConditions, userField, userHierarchy);
    }

    private AbstractCondition updateConditionsWithHierarchy(
            List<FilterCondition> conditions, String field, List<ULong> hierarchy) {

        AbstractCondition hierarchyCondition = new FilterCondition()
                .setField(field)
                .setOperator(FilterConditionOperator.IN)
                .setMultiValue(hierarchy);

        if (conditions.isEmpty()) return hierarchyCondition;

        List<AbstractCondition> combinedConditions = new ArrayList<>(conditions);

        return ComplexCondition.and(ComplexCondition.or(combinedConditions), hierarchyCondition);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<Page<D>> readPageFilterWithTimezone(Pageable pageable, AbstractCondition condition, String timezone) {

        if (condition.hasGroupCondition())
            return this.readPageFilterWithTimezone(
                    pageable, condition.getWhereCondition(), condition.getGroupCondition(), timezone);

        return FlatMapUtil.flatMapMono(
                this::getSelectJointStep,
                selectJoinStepTuple -> (this).filter(condition, selectJoinStepTuple.getT1(), timezone),
                (selectJoinStepTuple, jCondition) -> this.list(
                        pageable,
                        selectJoinStepTuple
                                .mapT1(e -> (SelectJoinStep<Record>) e.where(jCondition))
                                .mapT2(e -> (SelectJoinStep<Record1<Integer>>) e.where(jCondition))));
    }

    protected Mono<Page<D>> readPageFilterWithTimezone(
            Pageable pageable, AbstractCondition condition, AbstractCondition groupCondition, String timezone) {

        if (groupCondition == null || groupCondition.isEmpty())
            return this.readPageFilterWithTimezone(pageable, condition, timezone);

        return FlatMapUtil.flatMapMono(
                () -> this.getSelectJointStep(groupCondition),
                selectJoinStepTuple -> (this).filter(condition, selectJoinStepTuple.getT1(), timezone),
                (selectJoinStepTuple, whereCondition) -> this.filterHaving(groupCondition, selectJoinStepTuple.getT1()),
                (selectJoinStepTuple, whereCondition, havingCondition) -> this.list(
                        pageable,
                        this.applyGroupByAndHaving(
                                selectJoinStepTuple, whereCondition, havingCondition, groupCondition)));
    }

    @Override
    public Flux<D> readAllFilterWithTimezone(AbstractCondition condition, String timezone) {
        return this.getSelectJointStep().map(Tuple2::getT1).flatMapMany(sjs -> (this)
                .filter(condition, sjs, timezone)
                .flatMapMany(cond -> Flux.from(sjs.where(cond)).map(e -> e.into(this.pojoClass))));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Mono<Page<Map<String, Object>>> readPageFilterEagerWithTimezone(
            Pageable pageable,
            AbstractCondition condition,
            List<String> fields,
            String timezone,
            MultiValueMap<String, String> queryParams,
            Map<String, AbstractCondition> subQueryConditions) {

        if (condition.hasGroupCondition())
            return this.readPageFilterEagerWithTimezone(
                    pageable,
                    condition.getWhereCondition(),
                    condition.getGroupCondition(),
                    fields,
                    timezone,
                    queryParams,
                    subQueryConditions);

        return this.getSelectJointStepEager(fields, queryParams, subQueryConditions)
                .flatMap(tuple -> {
                    Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> selectJoinStepTuple =
                            tuple.getT1();
                    Map<String, Tuple2<Table<?>, String>> relations = tuple.getT2();

                    return (this)
                            .filter(condition, selectJoinStepTuple.getT1(), timezone)
                            .flatMap(filterCondition -> {
                                Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> filteredQueries =
                                        selectJoinStepTuple
                                                .mapT1(e -> (SelectJoinStep<Record>) e.where(filterCondition))
                                                .mapT2(e ->
                                                        (SelectJoinStep<Record1<Integer>>) e.where(filterCondition));

                                return (this).listAsMapWithTimezone(pageable, filteredQueries, relations, queryParams);
                            });
                });
    }

    protected Mono<Page<Map<String, Object>>> readPageFilterEagerWithTimezone(
            Pageable pageable,
            AbstractCondition condition,
            AbstractCondition groupCondition,
            List<String> fields,
            String timezone,
            MultiValueMap<String, String> queryParams,
            Map<String, AbstractCondition> subQueryConditions) {

        if (groupCondition == null || groupCondition.isEmpty())
            return this.readPageFilterEagerWithTimezone(
                    pageable, condition, fields, timezone, queryParams, subQueryConditions);

        return this.getSelectJointStepEager(fields, queryParams, subQueryConditions)
                .flatMap(tuple -> {
                    Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> selectJoinStepTuple =
                            tuple.getT1();
                    Map<String, Tuple2<Table<?>, String>> relations = tuple.getT2();

                    return (this)
                            .filter(condition, selectJoinStepTuple.getT1(), timezone)
                            .flatMap(filterCondition -> (this)
                                    .filterHaving(groupCondition, selectJoinStepTuple.getT1())
                                    .map(havingCondition -> this.applyGroupByAndHaving(
                                            selectJoinStepTuple, filterCondition, havingCondition, groupCondition))
                                    .flatMap(finalQueries -> (this)
                                            .listAsMapWithTimezone(pageable, finalQueries, relations, queryParams)));
                });
    }
}
