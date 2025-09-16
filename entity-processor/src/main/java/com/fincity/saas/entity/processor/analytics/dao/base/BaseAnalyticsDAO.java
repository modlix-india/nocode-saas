package com.fincity.saas.entity.processor.analytics.dao.base;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.entity.processor.analytics.model.BucketFilter;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    public Mono<AbstractCondition> createBucketConditions(ProcessorAccess access, BucketFilter bucketFilter) {
        return this.addBucketConditions(null, access, bucketFilter);
    }

    public Mono<AbstractCondition> createBucketConditions(
            AbstractCondition condition, ProcessorAccess access, BucketFilter bucketFilter) {
        return this.addBucketConditions(condition, access, bucketFilter);
    }

    private Mono<AbstractCondition> addBucketConditions(
            AbstractCondition baseCondition, ProcessorAccess access, BucketFilter filter) {

        if (filter == null && baseCondition == null) return Mono.just(new FilterCondition());

        return Mono.fromCallable(() -> {
                    Map<String, String> fieldMappings = this.getBucketFilterFieldMappings();
                    List<AbstractCondition> conditions = new ArrayList<>();

                    if (baseCondition != null && !baseCondition.isEmpty()) conditions.add(baseCondition);

                    if (filter != null) {
                        this.buildUserConditions(conditions, access, filter, fieldMappings);
                        this.buildSourceConditions(conditions, filter, fieldMappings);
                        this.buildDateConditions(conditions, filter, fieldMappings);
                    }

                    return switch (conditions.size()) {
                        case 0 -> new FilterCondition();
                        case 1 -> conditions.getFirst();
                        default -> ComplexCondition.and(conditions.toArray(new AbstractCondition[0]));
                    };
                })
                .onErrorReturn(new FilterCondition());
    }

    private void buildUserConditions(
            List<AbstractCondition> conditions,
            ProcessorAccess access,
            BucketFilter filter,
            Map<String, String> fieldMappings) {

        List<ULong> effectiveUserIds = this.getEffectiveUserIds(access, filter);
        this.addInConditionIfPresent(conditions, effectiveUserIds, BucketFilter.Fields.userIds, fieldMappings);
    }

    private void buildSourceConditions(
            List<AbstractCondition> conditions, BucketFilter filter, Map<String, String> fieldMappings) {

        this.addInConditionIfPresent(conditions, filter.getSources(), BucketFilter.Fields.sources, fieldMappings);
        this.addInConditionIfPresent(conditions, filter.getSubSources(), BucketFilter.Fields.subSources, fieldMappings);
        this.addInConditionIfPresent(conditions, filter.getProductIds(), BucketFilter.Fields.productIds, fieldMappings);
    }

    private void buildDateConditions(
            List<AbstractCondition> conditions, BucketFilter filter, Map<String, String> fieldMappings) {

        if ((filter.getStartDate() == null && filter.getEndDate() == null)) return;

        if (filter.getStartDate() != null && filter.getEndDate() != null) {
            conditions.add(new FilterCondition()
                    .setField(fieldMappings.get(BucketFilter.Fields.startDate))
                    .setOperator(FilterConditionOperator.BETWEEN)
                    .setValue(filter.getStartDate())
                    .setToValue(filter.getEndDate()));
        } else if (filter.getStartDate() != null) {
            conditions.add(new FilterCondition()
                    .setField(fieldMappings.get(BucketFilter.Fields.startDate))
                    .setOperator(FilterConditionOperator.GREATER_THAN_EQUAL)
                    .setValue(filter.getStartDate()));
        } else {
            conditions.add(new FilterCondition()
                    .setField(fieldMappings.get(BucketFilter.Fields.startDate))
                    .setOperator(FilterConditionOperator.LESS_THAN_EQUAL)
                    .setValue(filter.getEndDate()));
        }
    }

    private List<ULong> getEffectiveUserIds(ProcessorAccess access, BucketFilter filter) {

        if (access.getSubOrg() == null || access.getSubOrg().isEmpty()) return List.of(access.getUserId());

        List<ULong> filterUserIds = filter != null ? filter.getUserIds() : null;
        if (filterUserIds == null || filterUserIds.isEmpty()) return access.getSubOrg();

        Set<ULong> subOrgSet = Set.copyOf(access.getSubOrg());

        return filterUserIds.stream().filter(subOrgSet::contains).toList();
    }

    private <T> void addInConditionIfPresent(
            List<AbstractCondition> conditions, List<T> values, String filterField, Map<String, String> fieldMappings) {

        if (values == null || values.isEmpty()) return;

        String mappedField = fieldMappings.get(filterField);
        if (mappedField != null)
            conditions.add(new FilterCondition()
                    .setField(mappedField)
                    .setOperator(FilterConditionOperator.IN)
                    .setMultiValue(values));
    }
}
