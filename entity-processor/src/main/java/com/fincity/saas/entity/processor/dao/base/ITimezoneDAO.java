package com.fincity.saas.entity.processor.dao.base;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.entity.processor.dto.base.BaseProcessorDto;
import com.fincity.saas.entity.processor.eager.IEagerDAO;
import com.fincity.saas.entity.processor.util.DatePair;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jooq.Condition;
import org.jooq.DataType;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectJoinStep;
import org.jooq.SelectLimitStep;
import org.jooq.SortField;
import org.jooq.SortOrder;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.impl.DSL;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

public interface ITimezoneDAO<R extends UpdatableRecord<R>, D extends BaseProcessorDto<D>> {

    private static Object convertNumeric(DataType<?> dt, Object value) {
        if (value instanceof Number) return value;

        String strValue = value.toString();

        return dt.hasPrecision() ? Double.parseDouble(strValue) : Long.parseLong(strValue);
    }

    private static Object convertTemporal(Object value, String timezone) {
        if (value instanceof LocalDateTime ldt) return DatePair.convertToUtc(ldt, timezone);

        if (value instanceof LocalDate ld) return DatePair.convertToUtc(ld.atStartOfDay(), timezone);

        if ("now".equals(value)) return LocalDateTime.now();

        long epochSeconds = Long.parseLong(value.toString());

        return DatePair.convertEpochSecondsToUtc(epochSeconds, timezone);
    }

    Mono<Page<D>> readPageFilter(Pageable pageable, AbstractCondition condition);

    Flux<D> readAll(AbstractCondition condition);

    default Mono<Page<D>> readPageFilter(Pageable pageable, AbstractCondition condition, String timezone) {
        return timezone == null || timezone.isBlank()
                ? this.readPageFilter(pageable, condition)
                : this.readPageFilterWithTimezone(pageable, condition, timezone);
    }

    default Flux<D> readAllFilter(AbstractCondition condition, String timezone) {
        return timezone == null || timezone.isBlank()
                ? this.readAll(condition)
                : this.readAllFilterWithTimezone(condition, timezone);
    }

    default Mono<Page<Map<String, Object>>> readPageFilterEager(
            Pageable pageable,
            AbstractCondition condition,
            List<String> fields,
            String timezone,
            MultiValueMap<String, String> queryParams) {
        return this.readPageFilterEager(pageable, condition, fields, timezone, queryParams, null);
    }

    default Mono<Page<Map<String, Object>>> readPageFilterEager(
            Pageable pageable,
            AbstractCondition condition,
            List<String> fields,
            String timezone,
            MultiValueMap<String, String> queryParams,
            Map<String, AbstractCondition> subQueryConditions) {
        return timezone == null || timezone.isBlank()
                ? ((IEagerDAO<?>) this).readPageFilterEager(pageable, condition, fields, queryParams, subQueryConditions)
                : this.readPageFilterEagerWithTimezone(
                        pageable, condition, fields, timezone, queryParams, subQueryConditions);
    }

    Mono<Page<D>> readPageFilterWithTimezone(Pageable pageable, AbstractCondition condition, String timezone);

    Flux<D> readAllFilterWithTimezone(AbstractCondition condition, String timezone);

    default Mono<Page<Map<String, Object>>> readPageFilterEagerWithTimezone(
            Pageable pageable,
            AbstractCondition condition,
            List<String> fields,
            String timezone,
            MultiValueMap<String, String> queryParams) {
        return this.readPageFilterEagerWithTimezone(pageable, condition, fields, timezone, queryParams, null);
    }

    Mono<Page<Map<String, Object>>> readPageFilterEagerWithTimezone(
            Pageable pageable,
            AbstractCondition condition,
            List<String> fields,
            String timezone,
            MultiValueMap<String, String> queryParams,
            Map<String, AbstractCondition> subQueryConditions);

    default Mono<Condition> filter(
            AbstractCondition condition, SelectJoinStep<Record> selectJoinStep, String timezone) {
        if (condition == null) return Mono.just(DSL.noCondition());

        return (condition instanceof ComplexCondition cc
                        ? this.complexConditionFilter(cc, selectJoinStep, timezone)
                        : Mono.just(this.filterConditionFilter((FilterCondition) condition, selectJoinStep, timezone)))
                .map(c -> condition.isNegate() ? c.not() : c);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    default Condition filterConditionFilter(
            FilterCondition fc, SelectJoinStep<Record> selectJoinStep, String timezone) { // NO SONAR
        IEagerDAO<?> eagerDao = (IEagerDAO<?>) this;
        Field field = eagerDao.getField(fc.getField(), selectJoinStep); // NO SONAR

        if (field == null) return DSL.noCondition();

        if (fc.getOperator() == FilterConditionOperator.BETWEEN) {
            return field.between(
                            fc.isValueField()
                                    ? (Field<?>) eagerDao.getField(fc.getField(), selectJoinStep)
                                    : this.fieldValue(field, fc.getValue(), timezone))
                    .and(
                            fc.isToValueField()
                                    ? (Field<?>) eagerDao.getField(fc.getField(), selectJoinStep)
                                    : this.fieldValue(field, fc.getToValue(), timezone));
        }

        if (fc.getOperator() == FilterConditionOperator.EQUALS
                || fc.getOperator() == FilterConditionOperator.GREATER_THAN
                || fc.getOperator() == FilterConditionOperator.GREATER_THAN_EQUAL
                || fc.getOperator() == FilterConditionOperator.LESS_THAN
                || fc.getOperator() == FilterConditionOperator.LESS_THAN_EQUAL) {
            if (fc.isValueField()) {
                if (fc.getField() == null) return DSL.noCondition();
                return switch (fc.getOperator()) {
                    case EQUALS -> field.eq(eagerDao.getField(fc.getField(), selectJoinStep));
                    case GREATER_THAN -> field.gt(eagerDao.getField(fc.getField(), selectJoinStep));
                    case GREATER_THAN_EQUAL -> field.ge(eagerDao.getField(fc.getField(), selectJoinStep));
                    case LESS_THAN -> field.lt(eagerDao.getField(fc.getField(), selectJoinStep));
                    case LESS_THAN_EQUAL -> field.le(eagerDao.getField(fc.getField(), selectJoinStep));
                    default -> DSL.noCondition();
                };
            }

            if (fc.getValue() == null) return DSL.noCondition();
            Object v = this.fieldValue(field, fc.getValue(), timezone);
            return switch (fc.getOperator()) {
                case EQUALS -> field.eq(this.fieldValue(field, v, timezone));
                case GREATER_THAN -> field.gt(this.fieldValue(field, v, timezone));
                case GREATER_THAN_EQUAL -> field.ge(this.fieldValue(field, v, timezone));
                case LESS_THAN -> field.lt(this.fieldValue(field, v, timezone));
                case LESS_THAN_EQUAL -> field.le(this.fieldValue(field, v, timezone));
                default -> DSL.noCondition();
            };
        }

        return switch (fc.getOperator()) {
            case IS_FALSE -> field.isFalse();
            case IS_TRUE -> field.isTrue();
            case IS_NULL -> field.isNull();
            case IN -> field.in(this.multiFieldValue(field, fc.getValue(), fc.getMultiValue(), timezone));
            case LIKE -> field.like(fc.getValue().toString());
            case STRING_LOOSE_EQUAL -> field.like("%" + fc.getValue() + "%");
            default -> DSL.noCondition();
        };
    }

    default Mono<Condition> complexConditionFilter(
            ComplexCondition cc, SelectJoinStep<Record> selectJoinStep, String timezone) {

        if (cc.getConditions() == null || cc.getConditions().isEmpty()) return Mono.just(DSL.noCondition());

        return Flux.concat(cc.getConditions().stream()
                        .map(condition -> this.filter(condition, selectJoinStep, timezone))
                        .toList())
                .collectList()
                .map(conditions ->
                        cc.getOperator() == ComplexConditionOperator.AND ? DSL.and(conditions) : DSL.or(conditions));
    }

    default Object fieldValue(Field<?> field, Object value, String timezone) {
        if (value == null) return null;

        DataType<?> dt = field.getDataType();

        if (dt.isString() || dt.isJSON() || dt.isEnum()) return value.toString();

        if (dt.isNumeric()) return convertNumeric(dt, value);

        if (dt.isDate() || dt.isDateTime() || dt.isTime() || dt.isTimestamp()) return convertTemporal(value, timezone);

        return value;
    }

    default List<?> multiFieldValue(Field<?> field, Object obValue, List<?> values, String timezone) { // NOSONAR

        if (values != null && !values.isEmpty()) return values;

        if (obValue == null) return List.of();

        int from = 0;
        String iValue = obValue.toString().trim();

        List<Object> obj = new ArrayList<>();
        for (int i = 0; i <= iValue.length(); i++) { // NOSONAR

            if (i < iValue.length() && iValue.charAt(i) != ',') continue;

            if (i > 0 && iValue.charAt(i - 1) == '\\') continue;

            String str = iValue.substring(from, i).trim();

            if (str.isEmpty()) continue;

            obj.add(this.fieldValue(field, str, timezone));
            from = i + 1;
        }

        return obj;
    }

    default Mono<Page<Map<String, Object>>> listAsMapWithTimezone(
            Pageable pageable,
            Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> selectJoinStepTuple,
            Map<String, Tuple2<Table<?>, String>> relations,
            MultiValueMap<String, String> queryParams) {

        IEagerDAO<?> eagerDao = (IEagerDAO<?>) this;
        List<SortField<?>> orderBy = new ArrayList<>();

        pageable.getSort().forEach(order -> {
            Field<?> field = eagerDao.getField(order.getProperty(), selectJoinStepTuple.getT1());
            if (field != null)
                orderBy.add(field.sort(order.getDirection() == Sort.Direction.ASC ? SortOrder.ASC : SortOrder.DESC));
        });

        Mono<Integer> recsCount = Mono.from(selectJoinStepTuple.getT2()).map(Record1::value1);

        SelectJoinStep<Record> baseQuery = selectJoinStepTuple.getT1();
        SelectLimitStep<Record> finalQuery = orderBy.isEmpty() ? baseQuery : baseQuery.orderBy(orderBy);

        Mono<List<Map<String, Object>>> recsList = Flux.from(
                        finalQuery.limit(pageable.getPageSize()).offset(pageable.getOffset()))
                .map(rec -> eagerDao.processRelatedData(rec.intoMap(), relations))
                .collectList()
                .flatMap(recs -> eagerDao.getRecordEnrichmentService() != null
                        ? eagerDao.getRecordEnrichmentService()
                                .enrich(recs, eagerDao.getRelationResolverMap(), queryParams)
                        : Mono.just(recs));

        return Mono.zip(recsList, recsCount)
                .map(tuple -> PageableExecutionUtils.getPage(tuple.getT1(), pageable, tuple::getT2));
    }
}
