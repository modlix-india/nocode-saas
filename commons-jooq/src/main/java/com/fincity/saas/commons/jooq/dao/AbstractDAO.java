package com.fincity.saas.commons.jooq.dao;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
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

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.dto.AbstractDTO;

import lombok.Getter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

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

    public Mono<Page<D>> readPage(Pageable pageable) {
        return this.getSelectJointStep().flatMap(tup -> this.list(pageable, tup));
    }

    @SuppressWarnings("unchecked")
    public Mono<Page<D>> readPageFilter(Pageable pageable, AbstractCondition condition) {

        return FlatMapUtil.flatMapMono(
                this::getSelectJointStep,
                selectJoinStepTuple -> this.filter(condition, selectJoinStepTuple.getT1()),
                (selectJoinStepTuple, jCondition) -> this.list(
                        pageable,
                        selectJoinStepTuple
                                .mapT1(e -> (SelectJoinStep<Record>) e.where(jCondition))
                                .mapT2(e -> (SelectJoinStep<Record1<Integer>>) e.where(jCondition))));
    }

    protected Mono<Page<D>> list(
            Pageable pageable, Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> selectJoinStepTuple) {
        List<SortField<?>> orderBy = new ArrayList<>();

        pageable.getSort().forEach(order -> {
            Field<?> field = this.getField(order.getProperty(), selectJoinStepTuple.getT1());
            if (field != null)
                orderBy.add(field.sort(order.getDirection() == Direction.ASC ? SortOrder.ASC : SortOrder.DESC));
        });

        final Mono<Integer> recordsCount =
                Mono.from(selectJoinStepTuple.getT2()).map(Record1::value1);

        SelectJoinStep<Record> selectJoinStep = selectJoinStepTuple.getT1();
        if (!orderBy.isEmpty()) {
            selectJoinStep.orderBy(orderBy);
        }

        Mono<List<D>> recordsList = Flux.from(
                        selectJoinStep.limit(pageable.getPageSize()).offset(pageable.getOffset()))
                .map(e -> e.into(this.pojoClass))
                .collectList();

        return recordsList.flatMap(
                list -> recordsCount.map(count -> PageableExecutionUtils.getPage(list, pageable, () -> count)));
    }

    public Flux<D> readAll(AbstractCondition query) {
        return this.getSelectJointStep().map(Tuple2::getT1).flatMapMany(sjs -> this.filter(query)
                .flatMapMany(cond -> Flux.from(sjs.where(cond)).map(e -> e.into(this.pojoClass))));
    }

    public Mono<D> readById(I id) {
        return this.getRecordById(id).map(e -> e.into(this.pojoClass));
    }

    protected String convertToJOOQFieldName(String fieldName) {
        return fieldName.replaceAll("([A-Z])", "_$1").toUpperCase();
    }

    public Mono<D> create(D pojo) {

        pojo.setId(null);

        R rec = dslContext.newRecord(this.table);
        rec.from(pojo);

        return Mono.from(dslContext.insertInto(this.table).set(rec).returning(this.idField))
                .map(r -> r.get(0, this.idField.getType()))
                .flatMap(id -> Mono.from(dslContext
                        .selectFrom(this.table)
                        .where(this.idField.eq(id))
                        .limit(1)))
                .map(r -> r.into(this.pojoClass));
    }

    public Mono<Integer> delete(I id) {

        DeleteQuery<R> query = dslContext.deleteQuery(table);
        query.addConditions(idField.eq(id));

        return Mono.from(query);
    }

    @SuppressWarnings("rawtypes")
    public Field getField(String fieldName) {
        return this.getField(fieldName, null);
    }

    public Field getField(String fieldName, SelectJoinStep<Record> selectJoinStep) { // NOSONAR
        String jooqFieldName = this.convertToJOOQFieldName(fieldName);

        Field field = table.field(jooqFieldName); // NOSONAR
        if (field != null) return field;

        if (selectJoinStep != null) {
            try {
                return selectJoinStep.getSelect().stream()
                        .filter(f -> f.getName().equalsIgnoreCase(jooqFieldName))
                        .findFirst()
                        .orElse(DSL.field(jooqFieldName));
            } catch (ClassCastException e) {
                logger.warn(
                        "Could not cast SelectJoinStep to SelectQuery for field: {}. Falling back to unattached Field.",
                        jooqFieldName,
                        e);
            }
        }

        return DSL.field(jooqFieldName);
    }

    public Mono<Condition> filter(AbstractCondition condition) {
        return this.filter(condition, null);
    }

    public Mono<Condition> filter(AbstractCondition condition, SelectJoinStep<Record> selectJoinStep) {

        if (condition == null) return Mono.just(DSL.noCondition());

        return (condition instanceof ComplexCondition cc
                        ? this.complexConditionFilter(cc, selectJoinStep)
                        : Mono.just(this.filterConditionFilter((FilterCondition) condition, selectJoinStep)))
                .map(c -> condition.isNegate() ? c.not() : c);
    }

    protected Condition filterConditionFilter(FilterCondition fc) {
        return this.filterConditionFilter(fc, null);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected Condition filterConditionFilter(FilterCondition fc, SelectJoinStep<Record> selectJoinStep) { // NO SONAR
        // Just 16 beyond the limit.

        Field field = this.getField(fc.getField(), selectJoinStep); // NO SONAR
        // Field has to be a raw type because we are generalizing

        if (field == null) return DSL.noCondition();

        if (fc.getOperator() == FilterConditionOperator.BETWEEN) {
            return field.between(
                            fc.isValueField()
                                    ? (Field<?>) this.getField(fc.getField(), selectJoinStep)
                                    : this.fieldValue(field, fc.getValue()))
                    .and(
                            fc.isToValueField()
                                    ? (Field<?>) this.getField(fc.getField(), selectJoinStep)
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
                    case EQUALS -> field.eq(this.getField(fc.getField(), selectJoinStep));
                    case GREATER_THAN -> field.gt(this.getField(fc.getField(), selectJoinStep));
                    case GREATER_THAN_EQUAL -> field.ge(this.getField(fc.getField(), selectJoinStep));
                    case LESS_THAN -> field.lt(this.getField(fc.getField(), selectJoinStep));
                    case LESS_THAN_EQUAL -> field.le(this.getField(fc.getField(), selectJoinStep));
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

            return value.equals("now")
                    ? LocalDateTime.now()
                    : LocalDateTime.ofEpochSecond(Long.parseLong(value.toString()), 0, ZoneOffset.UTC);
        }

        return value;
    }

    protected Mono<Condition> complexConditionFilter(ComplexCondition cc) {
        return this.complexConditionFilter(cc, null);
    }

    protected Mono<Condition> complexConditionFilter(ComplexCondition cc, SelectJoinStep<Record> selectJoinStep) {

        if (cc.getConditions() == null || cc.getConditions().isEmpty()) return Mono.just(DSL.noCondition());

        return Flux.concat(cc.getConditions().stream()
                        .map(condition -> this.filter(condition, selectJoinStep))
                        .toList())
                .collectList()
                .map(conditions ->
                        cc.getOperator() == ComplexConditionOperator.AND ? DSL.and(conditions) : DSL.or(conditions));
    }

    protected Mono<Record> getRecordById(I id) {
        return this.getSelectJointStep()
                .map(Tuple2::getT1)
                .flatMap(e -> Mono.from(e.where(idField.eq(id))))
                .switchIfEmpty(Mono.defer(() -> messageResourceService
                        .getMessage(OBJECT_NOT_FOUND, this.pojoClass.getSimpleName(), id)
		                .handle((msg, sink) -> {
			                sink.error(new GenericException(HttpStatus.NOT_FOUND, msg));
		                })));
    }

    protected Mono<Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>>> getSelectJointStep() {
        return Mono.just(Tuples.of(
                dslContext.select(Arrays.asList(table.fields())).from(table),
                dslContext.select(DSL.count()).from(table)));
    }

    public Mono<Class<D>> getPojoClass() {
        return Mono.just(this.pojoClass);
    }
}
