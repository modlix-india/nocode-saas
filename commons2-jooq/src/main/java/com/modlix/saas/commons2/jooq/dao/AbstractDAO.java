package com.modlix.saas.commons2.jooq.dao;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.DataType;
import org.jooq.DeleteQuery;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectJoinStep;
import org.jooq.SortField;
import org.jooq.SortOrder;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import com.modlix.saas.commons2.configuration.service.AbstractMessageService;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.model.condition.AbstractCondition;
import com.modlix.saas.commons2.model.condition.ComplexCondition;
import com.modlix.saas.commons2.model.condition.ComplexConditionOperator;
import com.modlix.saas.commons2.model.condition.FilterCondition;
import com.modlix.saas.commons2.model.condition.FilterConditionOperator;
import com.modlix.saas.commons2.model.condition.GroupCondition;
import com.modlix.saas.commons2.model.condition.HavingCondition;
import com.modlix.saas.commons2.model.dto.AbstractDTO;
import com.modlix.saas.commons2.util.Tuples;
import com.modlix.saas.commons2.util.Tuples.Tuple2;

import lombok.Getter;

@Getter
@Transactional
public abstract class AbstractDAO<R extends UpdatableRecord<R>, I extends Serializable, D extends AbstractDTO<I, I>> {

    private static final String OBJECT_NOT_FOUND = AbstractMessageService.OBJECT_NOT_FOUND;

    protected final Class<D> pojoClass;

    protected final Logger logger;
    protected final Table<R> table;
    protected final Field<I> idField;

    @Autowired // NOSONAR
    protected DSLContext dslContext;

    @Autowired // NOSONAR
    protected AbstractMessageService messageResourceService;

    protected AbstractDAO(Class<D> pojoClass, Table<R> table, Field<I> idField) {

        this.pojoClass = pojoClass;
        this.table = table;
        this.idField = idField;
        this.logger = LoggerFactory.getLogger(this.getClass());
    }

    @SuppressWarnings("unchecked")
    public Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> applyGroupByAndHaving(
            Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> selectJoinStepTuple,
            Condition whereCondition,
            Condition havingCondition,
            AbstractCondition groupCondition) {

        List<Field<?>> groupByFields = this.extractGroupByFields(groupCondition, selectJoinStepTuple.getT1());
        var selectQuery = selectJoinStepTuple.getT1().where(whereCondition);
        var countQuery = selectJoinStepTuple.getT2().where(whereCondition);

        if (!groupByFields.isEmpty()) {
            var groupedSelect = selectQuery.groupBy(groupByFields);
            var groupedCount = countQuery.groupBy(groupByFields);
            if (havingCondition != null && !DSL.noCondition().equals(havingCondition))
                return Tuples.of(
                        (SelectJoinStep<Record>) groupedSelect.having(havingCondition),
                        (SelectJoinStep<Record1<Integer>>) groupedCount.having(havingCondition));

            return Tuples.of((SelectJoinStep<Record>) groupedSelect, (SelectJoinStep<Record1<Integer>>) groupedCount);
        }
        return Tuples.of((SelectJoinStep<Record>) selectQuery, (SelectJoinStep<Record1<Integer>>) countQuery);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<Field<?>> extractGroupByFields(
            AbstractCondition groupCondition, SelectJoinStep<Record> selectJoinStep) {

        if (!(groupCondition instanceof GroupCondition gc) || gc.getFields() == null) return Collections.emptyList();

        List<Field<?>> groupByFields = new ArrayList<>(gc.getFields().size());

        for (String fieldName : gc.getFields()) {
            Field field = this.getField(fieldName, selectJoinStep);
            if (field != null)
                groupByFields.add(field);
        }

        return groupByFields;
    }

    @SuppressWarnings("unchecked")
    public Page<D> readPageFilter(Pageable pageable, AbstractCondition condition) {

        if (condition.hasGroupCondition())
            return this.readPageFilter(pageable, condition.getWhereCondition(), condition.getGroupCondition());

        Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> selectJoinStepTuple =
                this.getSelectJointStep();
        Condition jCondition = this.filter(condition, selectJoinStepTuple.getT1());
        return this.list(
                pageable,
                Tuples.of((SelectJoinStep<Record>) selectJoinStepTuple.getT1().where(jCondition), (SelectJoinStep<
                                Record1<Integer>>)
                        selectJoinStepTuple.getT2().where(jCondition)));
    }

    public Page<D> readPageFilter(
            Pageable pageable, AbstractCondition condition, AbstractCondition groupCondition) {

        if (groupCondition == null || groupCondition.isEmpty()) return this.readPageFilter(pageable, condition);

        Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> selectJoinStepTuple =
                this.getSelectJointStep(groupCondition);
        Condition whereCondition = this.filter(condition, selectJoinStepTuple.getT1());
        Condition havingCondition = this.filterHaving(groupCondition, selectJoinStepTuple.getT1());
        Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> withGroupBy =
                this.applyGroupByAndHaving(selectJoinStepTuple, whereCondition, havingCondition, groupCondition);
        return this.list(pageable, withGroupBy);
    }

    protected Page<D> list(
            Pageable pageable, Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> selectJoinStepTuple) {
        List<SortField<?>> orderBy = new ArrayList<>();

        pageable.getSort().forEach(order -> {
            Field<?> field = this.getField(order.getProperty(), selectJoinStepTuple.getT1());
            if (field != null)
                orderBy.add(field.sort(order.getDirection() == Direction.ASC ? SortOrder.ASC : SortOrder.DESC));
        });

        final Integer recordsCount =
                dslContext.fetchOne(selectJoinStepTuple.getT2()).value1();

        SelectJoinStep<Record> selectJoinStep = selectJoinStepTuple.getT1();
        if (!orderBy.isEmpty()) {
            selectJoinStep.orderBy(orderBy);
        }

        List<D> recordsList = dslContext
                .fetch(selectJoinStep.limit(pageable.getPageSize()).offset(pageable.getOffset()))
                .map(e -> e.into(this.pojoClass));

        return PageableExecutionUtils.getPage(recordsList, pageable, () -> recordsCount);
    }

    public List<D> readAll(AbstractCondition query) {
        Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> selectJoinStepTuple =
                this.getSelectJointStep();
        Condition condition = this.filter(query, selectJoinStepTuple.getT1());
        return dslContext.fetch(selectJoinStepTuple.getT1().where(condition)).map(e -> e.into(this.pojoClass));
    }

    public D readById(I id) {
        return this.getRecordById(id).into(this.pojoClass);
    }

    protected String convertToJOOQFieldName(String fieldName) {
        return fieldName.replaceAll("([A-Z])", "_$1").toUpperCase();
    }

    public D create(D pojo) {

        pojo.setId(null);

        R rec = dslContext.newRecord(this.table);
        rec.from(pojo);

        I id = dslContext
                .insertInto(this.table)
                .set(rec)
                .returning(this.idField)
                .fetchOne()
                .get(0, this.idField.getType());

        return dslContext
                .selectFrom(this.table)
                .where(this.idField.eq(id))
                .limit(1)
                .fetchOne()
                .into(this.pojoClass);
    }

    public Integer delete(I id) {

        DeleteQuery<R> query = dslContext.deleteQuery(table);
        query.addConditions(idField.eq(id));

        return query.execute();
    }

    @SuppressWarnings("rawtypes")
    public Field getField(String fieldName) {
        return this.getField(fieldName, null);
    }

    public Field getField(String fieldName, SelectJoinStep<Record> selectJoinStep) {
        String jooqFieldName = this.convertToJOOQFieldName(fieldName);

        Field field = table.field(jooqFieldName);

        if (field != null) return field;

        if (selectJoinStep != null) return this.findFieldInSelect(selectJoinStep, jooqFieldName);

        return null;
    }

    private Field findFieldInSelect(SelectJoinStep<Record> selectJoinStep, String jooqFieldName) {
        try {
            return selectJoinStep.getSelect().stream()
                    .filter(f -> f.getName().equalsIgnoreCase(jooqFieldName))
                    .findFirst()
                    .orElse(null);
        } catch (ClassCastException e) {
            logger.warn(
                    "Could not cast SelectJoinStep to SelectQuery for field: {}. Falling back to null.",
                    jooqFieldName,
                    e);
            return null;
        }
    }

    public Condition filter(AbstractCondition condition) {
        return this.filter(condition, null);
    }

    public Condition filter(AbstractCondition condition, SelectJoinStep<Record> selectJoinStep) {

        if (condition == null) return DSL.noCondition();

        Condition result = (condition instanceof ComplexCondition cc
                ? this.complexConditionFilter(cc, selectJoinStep)
                : this.filterConditionFilter((FilterCondition) condition, selectJoinStep));

        return condition.isNegate() ? result.not() : result;
    }

    public Condition filterHaving(AbstractCondition condition) {
        return this.filterHaving(condition, null);
    }

    public Condition filterHaving(AbstractCondition condition, SelectJoinStep<Record> selectJoinStep) {

        if (condition == null) return DSL.noCondition();

        Condition result = switch (condition) {
            case HavingCondition hc -> this.havingConditionFilter(hc, selectJoinStep);
            case GroupCondition gc -> gc.getHavingConditions().stream()
                    .map(hc -> this.havingConditionFilter(hc, selectJoinStep))
                    .reduce(
                            DSL.noCondition(),
                            ComplexConditionOperator.OR == gc.getOperator() ? Condition::or : Condition::and);
            case ComplexCondition cc -> {
                HavingCondition first = cc.findFirstHavingCondition();
                yield first != null ? this.havingConditionFilter(first, selectJoinStep) : DSL.noCondition();
            }
            default -> DSL.noCondition();
        };

        return condition.isNegate() ? result.not() : result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected Condition havingConditionFilter(HavingCondition hc, SelectJoinStep<Record> selectJoinStep) { // NOSONAR

        if (hc.getAggregateFunction() == null || hc.getCondition() == null)
            return DSL.noCondition();

        FilterCondition fc = hc.getCondition();

        Field baseField = this.getField(fc.getField(), selectJoinStep);

        if (baseField == null)
            return DSL.noCondition();

        Field aggregateField = switch (hc.getAggregateFunction()) {
            case MAX -> DSL.max(baseField);
            case MIN -> DSL.min(baseField);
            case SUM -> DSL.sum(baseField);
            case AVG -> DSL.avg(baseField);
            case COUNT -> DSL.count(baseField);
        };

        return this.buildConditionForField(
                aggregateField,
                fc.getOperator(),
                fc.isValueField(),
                fc.isToValueField(),
                fc.getValue(),
                fc.getToValue(),
                fc.getMultiValue(),
                fc.getField(),
                selectJoinStep);
    }

    @SuppressWarnings({"rawtypes"})
    protected Condition filterConditionFilter(FilterCondition fc, SelectJoinStep<Record> selectJoinStep) { // NO SONAR
        // Just 16 beyond the limit.

        Field field = this.getField(fc.getField(), selectJoinStep); // NO SONAR
        // Field has to be a raw type because we are generalizing

        if (field == null) return DSL.noCondition();

        return this.buildConditionForField(
                field,
                fc.getOperator(),
                fc.isValueField(),
                fc.isToValueField(),
                fc.getValue(),
                fc.getToValue(),
                fc.getMultiValue(),
                fc.getField(),
                selectJoinStep);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Condition buildConditionForField( // NOSONAR
            Field field,
            FilterConditionOperator operator,
            boolean isValueField,
            boolean isToValueField,
            Object value,
            Object toValue,
            List<?> multiValue,
            String otherFieldName,
            SelectJoinStep<Record> selectJoinStep) {

        if (operator == FilterConditionOperator.BETWEEN) {
            return field.between(
                            isValueField
                                    ? (Field<?>) this.getField(otherFieldName, selectJoinStep)
                                    : this.fieldValue(field, value))
                    .and(
                            isToValueField
                                    ? (Field<?>) this.getField(otherFieldName, selectJoinStep)
                                    : this.fieldValue(field, toValue));
        }

        if (operator == FilterConditionOperator.EQUALS
                || operator == FilterConditionOperator.GREATER_THAN
                || operator == FilterConditionOperator.GREATER_THAN_EQUAL
                || operator == FilterConditionOperator.LESS_THAN
                || operator == FilterConditionOperator.LESS_THAN_EQUAL) {
            if (isValueField) {
                if (otherFieldName == null) return DSL.noCondition();
                Field otherField = this.getField(otherFieldName, selectJoinStep);
                if (otherField == null) return DSL.noCondition();
                return switch (operator) {
                    case EQUALS -> field.eq(otherField);
                    case GREATER_THAN -> field.gt(otherField);
                    case GREATER_THAN_EQUAL -> field.ge(otherField);
                    case LESS_THAN -> field.lt(otherField);
                    case LESS_THAN_EQUAL -> field.le(otherField);
                    default -> DSL.noCondition();
                };
            }

            if (value == null) return DSL.noCondition();
            Object v = this.fieldValue(field, value);
            return switch (operator) {
                case EQUALS -> field.eq(this.fieldValue(field, v));
                case GREATER_THAN -> field.gt(this.fieldValue(field, v));
                case GREATER_THAN_EQUAL -> field.ge(this.fieldValue(field, v));
                case LESS_THAN -> field.lt(this.fieldValue(field, v));
                case LESS_THAN_EQUAL -> field.le(this.fieldValue(field, v));
                default -> DSL.noCondition();
            };
        }

        return switch (operator) {
            case IS_FALSE -> field.isFalse();
            case IS_TRUE -> field.isTrue();
            case IS_NULL -> field.isNull();
            case IN -> field.in(this.multiFieldValue(field, value, multiValue));
            case LIKE -> field.like(value.toString());
            case STRING_LOOSE_EQUAL -> field.like("%" + value + "%");
            default -> DSL.noCondition();
        };
    }

    protected List<?> multiFieldValue(Field<?> field, Object obValue, List<?> values) { // NOSONAR

        if (values != null && !values.isEmpty()) return values;

        if (obValue == null) return List.of();

        int from = 0;
        String iValue = obValue.toString().trim();

        List<Object> obj = new ArrayList<>();
        for (int i = 0; i <= iValue.length(); i++) { // NOSONAR
            // Having multiple continue statements is confusing

            if (i < iValue.length() && iValue.charAt(i) != ',') continue;

            if (i > 0 && iValue.charAt(i - 1) == '\\') continue;

            String str = iValue.substring(from, i).trim();

            if (str.isEmpty()) continue;

            obj.add(this.fieldValue(field, str));
            from = i + 1;
        }

        return obj;
    }

    protected Object fieldValue(Field<?> field, Object value) {

        if (value == null) return null;

        DataType<?> dt = field.getDataType();

        if (dt.isString() || dt.isJSON() || dt.isEnum()) return value.toString();

        if (dt.isNumeric()) {

            if (value instanceof Number) return value;

            if (dt.hasPrecision()) return Double.valueOf(value.toString());

            return Long.valueOf(value.toString());
        }

        if (dt.isDate() || dt.isDateTime() || dt.isTime() || dt.isTimestamp()) {

			if (value instanceof LocalDateTime || value instanceof LocalDate)
				return value;

            return value.equals("now")
                    ? LocalDateTime.now()
                    : LocalDateTime.ofEpochSecond(Long.parseLong(value.toString()), 0, ZoneOffset.UTC);
        }

        return value;
    }

    protected Condition complexConditionFilter(ComplexCondition cc, SelectJoinStep<Record> selectJoinStep) {

        if (cc.getConditions() == null || cc.getConditions().isEmpty()) return DSL.noCondition();

        List<Condition> conditions = cc.getConditions().stream()
                .map(condition -> this.filter(condition, selectJoinStep))
                .toList();

        return cc.getOperator() == ComplexConditionOperator.AND ? DSL.and(conditions) : DSL.or(conditions);
    }

    protected Record getRecordById(I id) {
        Record rec = this.getSelectJointStep().getT1().where(idField.eq(id)).fetchOne();

        if (rec == null) {
            String msg = messageResourceService.getDefaultLocaleMessage(
                    OBJECT_NOT_FOUND, this.pojoClass.getSimpleName(), id);
            throw new GenericException(HttpStatus.NOT_FOUND, msg);
        }

        return rec;
    }

    protected Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> getSelectJointStep() {
        return Tuples.of(
                dslContext.select(Arrays.asList(table.fields())).from(table),
                dslContext.select(DSL.count()).from(table));
    }

    protected Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> getSelectJointStep(
            AbstractCondition groupCondition) {
        return this.getSelectJointStep();
    }
}
