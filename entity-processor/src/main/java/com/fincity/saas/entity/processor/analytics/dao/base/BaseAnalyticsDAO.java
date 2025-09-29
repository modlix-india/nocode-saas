package com.fincity.saas.entity.processor.analytics.dao.base;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowUpdatableDTO;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.entity.processor.analytics.model.base.BaseFilter;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import reactor.core.publisher.Mono;

public abstract class BaseAnalyticsDAO<R extends UpdatableRecord<R>, D extends AbstractDTO<ULong, ULong>>
        extends AbstractDAO<R, ULong, D> {

    protected BaseAnalyticsDAO(Class<D> pojoClass, Table<R> table, Field<ULong> idField) {
        super(pojoClass, table, idField);
    }

    protected abstract Map<String, String> getBucketFilterFieldMappings();

    public <T extends BaseFilter<T>> Mono<AbstractCondition> createBucketConditions(
            ProcessorAccess access, T ticketBucketFilter) {
        return this.addBucketConditions(null, access, ticketBucketFilter);
    }

    public <T extends BaseFilter<T>> Mono<AbstractCondition> createBucketConditions(
            AbstractCondition condition, ProcessorAccess access, T ticketBucketFilter) {
        return this.addBucketConditions(condition, access, ticketBucketFilter);
    }

    private <T extends BaseFilter<T>> Mono<AbstractCondition> addBucketConditions(
            AbstractCondition baseCondition, ProcessorAccess access, T filter) {

        Map<String, String> fieldMappings = this.getBucketFilterFieldMappings();

        return Mono.zip(
                        this.getBaseAccessConditions(access, fieldMappings)
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()),
                        this.getDateConditions(filter, fieldMappings)
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()),
                        this.getFilterAccessConditions(filter, fieldMappings)
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()))
                .map(condTuple -> {
                    List<AbstractCondition> conditions = new ArrayList<>();

                    condTuple.getT1().ifPresent(conditions::add);
                    condTuple.getT2().ifPresent(conditions::add);
                    condTuple.getT3().ifPresent(conditions::add);

                    if (baseCondition != null && !baseCondition.isEmpty()) conditions.add(baseCondition);

                    return ComplexCondition.and(conditions.stream()
                            .filter(AbstractCondition::isNonEmpty)
                            .toList());
                });
    }

    private <T extends BaseFilter<T>> Mono<AbstractCondition> getFilterAccessConditions(
            T filter, Map<String, String> fieldMappings) {

        if (filter == null
                || filter.getAssignedUserIds() == null
                        && filter.getClientIds() == null
                        && filter.getCreatedByIds() == null) return Mono.empty();

        return Mono.zip(
                        this.makeIn(fieldMappings.get(BaseFilter.Fields.assignedUserIds), filter.getAssignedUserIds())
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()),
                        this.makeIn(fieldMappings.get(BaseFilter.Fields.clientIds), filter.getClientIds())
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()),
                        this.makeIn(fieldMappings.get(BaseFilter.Fields.createdByIds), filter.getCreatedByIds())
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()))
                .map(tuple -> {
                    List<AbstractCondition> conds = new ArrayList<>();
                    tuple.getT1().ifPresent(conds::add);
                    tuple.getT2().ifPresent(conds::add);
                    tuple.getT3().ifPresent(conds::add);
                    return ComplexCondition.and(conds);
                });
    }

    private Mono<AbstractCondition> getBaseAccessConditions(ProcessorAccess access, Map<String, String> fieldMappings) {

        return Mono.zip(
                        this.getAppCodeCondition(access).map(Optional::of).defaultIfEmpty(Optional.empty()),
                        this.getClientCodeCondition(access).map(Optional::of).defaultIfEmpty(Optional.empty()),
                        this.getUserConditions(access, fieldMappings)
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()),
                        this.getClientIdCondition(access, fieldMappings)
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty()))
                .map(condTuple -> {
                    List<AbstractCondition> appClientConditions = new ArrayList<>();
                    condTuple.getT1().ifPresent(appClientConditions::add);
                    condTuple.getT2().ifPresent(appClientConditions::add);

                    AbstractCondition appClientCondition = ComplexCondition.and(appClientConditions);

                    List<AbstractCondition> userClientConditions = new ArrayList<>();

                    condTuple.getT3().ifPresent(userClientConditions::add);
                    condTuple.getT4().ifPresent(userClientConditions::add);

                    AbstractCondition userClientCondition = access.isOutsideUser()
                            ? ComplexCondition.and(userClientConditions)
                            : ComplexCondition.or(userClientConditions);

                    return ComplexCondition.and(appClientCondition, userClientCondition);
                });
    }

    private Mono<AbstractCondition> getAppCodeCondition(ProcessorAccess access) {
        return Mono.just(FilterCondition.make(AbstractFlowUpdatableDTO.Fields.appCode, access.getAppCode()));
    }

    private Mono<AbstractCondition> getClientCodeCondition(ProcessorAccess access) {
        return Mono.just(
                FilterCondition.make(AbstractFlowUpdatableDTO.Fields.clientCode, access.getEffectiveClientCode()));
    }

    private Mono<AbstractCondition> getUserConditions(ProcessorAccess access, Map<String, String> fieldMappings) {

        if (access.isOutsideUser())
            return this.makeIn(
                    fieldMappings.get(BaseFilter.Fields.createdByIds),
                    access.getUserInherit().getSubOrg());

        return this.makeIn(
                fieldMappings.get(BaseFilter.Fields.assignedUserIds),
                access.getUserInherit().getSubOrg());
    }

    private Mono<AbstractCondition> getClientIdCondition(ProcessorAccess access, Map<String, String> fieldMappings) {

        if (access.isOutsideUser())
            return Mono.just(FilterCondition.make(
                    fieldMappings.get(BaseFilter.Fields.clientIds),
                    access.getUser().getClientId()));

        if (!access.isHasBpAccess()) return Mono.empty();

        return this.makeIn(
                fieldMappings.get(BaseFilter.Fields.clientIds),
                access.getUserInherit().getManagingClientIds());
    }

    private <T extends BaseFilter<T>> Mono<AbstractCondition> getDateConditions(
            T filter, Map<String, String> fieldMappings) {

        LocalDateTime startDate = filter.getStartDate();
        LocalDateTime endDate = filter.getEndDate();

        if (startDate == null && endDate == null) return Mono.empty();

        if (startDate != null && endDate != null)
            return Mono.just(new FilterCondition()
                    .setField(fieldMappings.get(BaseFilter.Fields.startDate))
                    .setOperator(FilterConditionOperator.BETWEEN)
                    .setValue(startDate.with(LocalTime.MIDNIGHT).toEpochSecond(ZoneOffset.UTC))
                    .setToValue(endDate.with(LocalTime.MAX).toEpochSecond(ZoneOffset.UTC)));

        if (startDate != null)
            return Mono.just(new FilterCondition()
                    .setField(fieldMappings.get(BaseFilter.Fields.startDate))
                    .setOperator(FilterConditionOperator.GREATER_THAN_EQUAL)
                    .setValue(startDate.with(LocalTime.MIDNIGHT).toEpochSecond(ZoneOffset.UTC)));

        return Mono.just(new FilterCondition()
                .setField(fieldMappings.get(BaseFilter.Fields.endDate))
                .setOperator(FilterConditionOperator.LESS_THAN_EQUAL)
                .setValue(endDate.with(LocalTime.MAX).toEpochSecond(ZoneOffset.UTC)));
    }

    protected <T> Mono<AbstractCondition> makeIn(String mappedField, List<T> values) {

        if (mappedField == null) return Mono.empty();

        if (values == null || values.isEmpty()) return Mono.empty();

        return Mono.just(new FilterCondition()
                .setField(mappedField)
                .setOperator(FilterConditionOperator.IN)
                .setMultiValue(values));
    }
}
