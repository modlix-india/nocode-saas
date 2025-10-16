package com.modlix.saas.commons2.jooq.dao;

import com.modlix.saas.commons2.configuration.service.AbstractMessageService;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.model.condition.AbstractCondition;
import com.modlix.saas.commons2.model.condition.ComplexCondition;
import com.modlix.saas.commons2.model.condition.ComplexConditionOperator;
import com.modlix.saas.commons2.model.condition.FilterCondition;
import com.modlix.saas.commons2.model.condition.FilterConditionOperator;
import com.modlix.saas.commons2.model.dto.AbstractDTO;
import com.modlix.saas.commons2.util.Tuples.Tuple2;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
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

@Getter
@Transactional
public abstract class AbstractDAO<R extends UpdatableRecord<R>, I extends Serializable, D extends AbstractDTO<I, I>> {

    private static final String OBJECT_NOT_FOUND = AbstractMessageService.OBJECT_NOT_FOUND;

    protected final Class<D> pojoClass;

    protected final Logger logger;

    @Autowired // NOSONAR
    protected DSLContext dslContext;

    @Autowired // NOSONAR
    protected AbstractMessageService messageResourceService;

    protected final Table<R> table;
    protected final Field<I> idField;

    protected AbstractDAO(Class<D> pojoClass, Table<R> table, Field<I> idField) {

        this.pojoClass = pojoClass;
        this.table = table;
        this.idField = idField;
        this.logger = LoggerFactory.getLogger(this.getClass());
    }

    public Page<D> readPage(Pageable pageable) {
        Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> selectJoinStepTuple = getSelectJointStep();
        return list(pageable, selectJoinStepTuple);
    }

    @SuppressWarnings("unchecked")
    public Page<D> readPageFilter(Pageable pageable, AbstractCondition condition) {
        Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> selectJoinStepTuple = getSelectJointStep();
        Condition filterCondition = filter(condition);

        SelectJoinStep<Record> selectJoinStep =
                (SelectJoinStep<Record>) selectJoinStepTuple.getT1().where(filterCondition);
        SelectJoinStep<Record1<Integer>> countJoinStep =
                (SelectJoinStep<Record1<Integer>>) selectJoinStepTuple.getT2().where(filterCondition);

        return list(pageable, new Tuple2<>(selectJoinStep, countJoinStep));
    }

    protected Page<D> list(
            Pageable pageable, Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> selectJoinStepTuple) {
        List<SortField<?>> orderBy = new ArrayList<>();

        pageable.getSort().forEach(order -> {
            Field<?> field = this.getField(order.getProperty());
            if (field != null)
                orderBy.add(field.sort(order.getDirection() == Direction.ASC ? SortOrder.ASC : SortOrder.DESC));
        });

        Integer recordsCount = dslContext.fetchOne(selectJoinStepTuple.getT2()).value1();

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
        SelectJoinStep<Record> selectJoinStep = getSelectJointStep().getT1();
        Condition condition = filter(query);

        if (condition != null) {
            selectJoinStep.where(condition);
        }

        return dslContext.fetch(selectJoinStep).map(e -> e.into(this.pojoClass));
    }

    public D readById(I id) {
        return getRecordById(id).into(this.pojoClass);
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
    public Field getField(String fieldName) { // NOSONAR
        // this return type has to be generic.
        return table.field(convertToJOOQFieldName(fieldName));
    }

    public Condition filter(AbstractCondition condition) {

        if (condition == null) return DSL.noCondition();

        Condition result = (condition instanceof ComplexCondition cc
                ? this.complexConditionFilter(cc)
                : this.filterConditionFilter((FilterCondition) condition));

        return condition.isNegate() ? result.not() : result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected Condition filterConditionFilter(FilterCondition fc) { // NOSONAR
        // Just 16 beyond the limit.

        Field field = this.getField(fc.getField()); // NOSONAR
        // Field has to be a raw type because we are generalising

        if (field == null) return DSL.noCondition();

        if (fc.getOperator() == FilterConditionOperator.BETWEEN) {
            return field.between(
                            fc.isValueField()
                                    ? (Field<?>) this.getField(fc.getField())
                                    : this.fieldValue(field, fc.getValue()))
                    .and(
                            fc.isToValueField()
                                    ? (Field<?>) this.getField(fc.getField())
                                    : this.fieldValue(field, fc.getToValue()));
        }

        if (fc.getOperator() == FilterConditionOperator.EQUALS
                || fc.getOperator() == FilterConditionOperator.GREATER_THAN
                || fc.getOperator() == FilterConditionOperator.GREATER_THAN_EQUAL
                || fc.getOperator() == FilterConditionOperator.LESS_THAN
                || fc.getOperator() == FilterConditionOperator.LESS_THAN_EQUAL) {
            if (fc.isValueField()) {
                if (fc.getField() == null) return DSL.noCondition();
                return switch (fc.getOperator()) {
                    case EQUALS -> field.eq(this.getField(fc.getField()));
                    case GREATER_THAN -> field.gt(this.getField(fc.getField()));
                    case GREATER_THAN_EQUAL -> field.ge(this.getField(fc.getField()));
                    case LESS_THAN -> field.lt(this.getField(fc.getField()));
                    case LESS_THAN_EQUAL -> field.le(this.getField(fc.getField()));
                    default -> DSL.noCondition();
                };
            }

            if (fc.getValue() == null) return DSL.noCondition();
            Object v = this.fieldValue(field, fc.getValue());
            return switch (fc.getOperator()) {
                case EQUALS -> field.eq(this.fieldValue(field, v));
                case GREATER_THAN -> field.gt(this.fieldValue(field, v));
                case GREATER_THAN_EQUAL -> field.ge(this.fieldValue(field, v));
                case LESS_THAN -> field.lt(this.fieldValue(field, v));
                case LESS_THAN_EQUAL -> field.le(this.fieldValue(field, v));
                default -> DSL.noCondition();
            };
        }

        return switch (fc.getOperator()) {
            case IS_FALSE -> field.isFalse();
            case IS_TRUE -> field.isTrue();
            case IS_NULL -> field.isNull();
            case IN -> field.in(this.multiFieldValue(field, fc.getValue(), fc.getMultiValue()));
            case LIKE -> field.like(fc.getValue().toString());
            case STRING_LOOSE_EQUAL -> field.like("%" + fc.getValue() + "%");
            default -> DSL.noCondition();
        };
    }

    protected List<?> multiFieldValue(Field<?> field, Object obValue, List<?> values) {

        if (values != null && !values.isEmpty()) return values;

        if (obValue == null) return List.of();

        int from = 0;
        String iValue = obValue.toString().trim();

        List<Object> obj = new ArrayList<>();
        for (int i = 0; i <= iValue.length(); i++) { // NOSONAR
            // Having multiple continue statements is not confusing

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

            return value.equals("now")
                    ? LocalDateTime.now()
                    : LocalDateTime.ofEpochSecond(Long.parseLong(value.toString()), 0, ZoneOffset.UTC);
        }

        return value;
    }

    protected Condition complexConditionFilter(ComplexCondition cc) {

        if (cc.getConditions() == null || cc.getConditions().isEmpty()) return DSL.noCondition();

        List<Condition> conditions =
                cc.getConditions().stream().map(this::filter).toList();

        return cc.getOperator() == ComplexConditionOperator.AND ? DSL.and(conditions) : DSL.or(conditions);
    }

    protected Record getRecordById(I id) {

        Record record = getSelectJointStep().getT1().where(idField.eq(id)).fetchOne();

        if (record == null) {
            String msg = messageResourceService.getDefaultLocaleMessage(
                    OBJECT_NOT_FOUND, this.pojoClass.getSimpleName(), id);
            throw new GenericException(HttpStatus.NOT_FOUND, msg);
        }

        return record;
    }

    protected Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> getSelectJointStep() {
        return new Tuple2<>(
                dslContext.select(Arrays.asList(table.fields())).from(table),
                dslContext.select(DSL.count()).from(table));
    }

    public Class<D> getPojoClass() {
        return this.pojoClass;
    }
}
