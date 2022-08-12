package com.fincity.security.dao;

import java.io.Serializable;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.DataType;
import org.jooq.DeleteQuery;
import org.jooq.Field;
import org.jooq.InsertSetStep;
import org.jooq.InsertValuesStepN;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectJoinStep;
import org.jooq.SortField;
import org.jooq.SortOrder;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.conf.ParamType;
import org.jooq.impl.DSL;
import org.jooq.types.UInteger;
import org.jooq.types.ULong;
import org.jooq.types.UNumber;
import org.jooq.types.UShort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import org.springframework.r2dbc.core.Parameter;
import org.springframework.transaction.annotation.Transactional;

import com.fincity.security.dto.AbstractDTO;
import com.fincity.security.exception.GenericException;
import com.fincity.security.model.condition.AbstractCondition;
import com.fincity.security.model.condition.ComplexCondition;
import com.fincity.security.model.condition.ComplexConditionOperator;
import com.fincity.security.model.condition.FilterCondition;
import com.fincity.security.service.MessageResourceService;

import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Transactional
public abstract class AbstractDAO<R extends UpdatableRecord<R>, I extends Serializable, D extends AbstractDTO<I, I>> {

	private static final String OBJECT_NOT_FOUND = "object_not_found";

	private static final Map<Class<?>, Function<UNumber, Tuple2<Object, Class<?>>>> CONVERTERS = Map.of(ULong.class,
	        x -> Tuples.of(x == null ? x : x.toBigInteger(), BigInteger.class), UInteger.class,
	        x -> Tuples.of(x == null ? x : x.longValue(), Long.class), UShort.class,
	        x -> Tuples.of(x == null ? x : x.intValue(), Integer.class));

	protected final Class<D> pojoClass;

	@Autowired
	protected DSLContext dslContext;

	@Autowired
	protected MessageResourceService messageResourceService;

	@Autowired
	protected DatabaseClient dbClient;

	protected final Table<R> table;
	protected final Field<I> idField;

	protected AbstractDAO(Class<D> pojoClass, Table<R> table, Field<I> idField) {

		this.pojoClass = pojoClass;
		this.table = table;
		this.idField = idField;
	}

	public Mono<Page<D>> readPage(Pageable pageable) {
		Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> selectJoinStepTuple = getSelectJointStep();
		return list(pageable, selectJoinStepTuple);
	}

	@SuppressWarnings("unchecked")
	public Mono<Page<D>> readPageFilter(Pageable pageable, AbstractCondition condition) {
		Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> selectJoinStepTuple = getSelectJointStep();

		if (condition != null) {
			Condition filterCondition = filter(condition);
			selectJoinStepTuple = selectJoinStepTuple.mapT1(e -> (SelectJoinStep<Record>) e.where(filterCondition))
			        .mapT2(e -> (SelectJoinStep<Record1<Integer>>) e.where(filterCondition));
		}
		return list(pageable, selectJoinStepTuple);
	}

	protected Mono<Page<D>> list(Pageable pageable,
	        Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> selectJoinStepTuple) {
		List<SortField<?>> orderBy = new ArrayList<>();

		pageable.getSort()
		        .forEach(order ->
				{
			        Field<?> field = this.getField(order.getProperty());
			        if (field != null)
				        orderBy.add(field.sort(order.getDirection() == Direction.ASC ? SortOrder.ASC : SortOrder.DESC));
		        });

		final Mono<Integer> recordsCount = Mono.from(selectJoinStepTuple.getT2())
		        .map(Record1::value1);

		SelectJoinStep<Record> selectJoinStep = selectJoinStepTuple.getT1();
		if (!orderBy.isEmpty()) {
			selectJoinStep.orderBy(orderBy);
		}

		Mono<List<D>> recordsList = Flux.from(selectJoinStep.limit(pageable.getPageSize())
		        .offset(pageable.getOffset()))
		        .map(e -> e.into(this.pojoClass))
		        .collectList();

		return recordsList.flatMap(
		        list -> recordsCount.map(count -> PageableExecutionUtils.getPage(list, pageable, () -> count)));
	}

	public Flux<D> readAll(AbstractCondition query) {
		SelectJoinStep<Record> selectJoinStep = getSelectJointStep().getT1();
		selectJoinStep.where(filter(query));

		return Flux.from(selectJoinStep)
		        .map(e -> e.into(this.pojoClass));
	}

	public Mono<D> readById(I id) {
		return this.getRecordById(id)
		        .map(e -> e.into(this.pojoClass));
	}

	protected String convertToJOOQFieldName(String fieldName) {
		return fieldName.replaceAll("([A-Z])", "_$1")
		        .toUpperCase();
	}

	@SuppressWarnings("unchecked")
	public Mono<D> create(D pojo) {

		pojo.setId(null);

		R rec = this.dslContext.newRecord(this.table);
		rec.from(pojo);

		List<Tuple2<Field<?>, Object>> values = new LinkedList<>();

		InsertSetStep<R> insertQuery = this.dslContext.insertInto(this.table);

		for (Field<?> eachField : this.table.fields()) {

			Object value = rec.get(eachField);
			if (value == null)
				continue;
			values.add(Tuples.of(eachField, value));
		}

		InsertValuesStepN<R> query = insertQuery.columns(values.stream()
		        .map(Tuple2::getT1)
		        .toList())
		        .values(values.stream()
		                .map(Tuple2::getT2)
		                .toList());

		String sql = query.getSQL(ParamType.NAMED);
		GenericExecuteSpec querySpec = this.dbClient.sql(sql)
		        .filter((statement, executeFunction) -> statement.returnGeneratedValues(this.idField.getName())
		                .execute());

		for (int i = 0; i < values.size(); i++) {

			Field<?> eachField = values.get(i)
			        .getT1();
			Object v = values.get(i)
			        .getT2();
			Class<?> classs = eachField.getType();

			if (CONVERTERS.containsKey(v.getClass())) {

				Tuple2<Object, Class<?>> x = CONVERTERS.get(v.getClass())
				        .apply((UNumber) v);
				v = x.getT1();
				classs = x.getT2();
			}

			querySpec = querySpec.bind(Integer.toString(i + 1), Parameter.fromOrEmpty(v, classs));
		}

		Mono<I> id = querySpec.fetch()
		        .first()
		        .map(e -> (I) e.get(this.idField.getName()));

		String selectQuery = this.dslContext.select(this.table.fields())
		        .from(this.table)
		        .getSQL() + " where " + this.idField.getName() + " = ";

		return id.map(i -> dbClient.sql(selectQuery + i + " limit 1"))
		        .flatMap(spec -> spec.map(this::rowMapper)
		                .first());
	}

	public Mono<Void> delete(I id) {

		DeleteQuery<R> query = dslContext.deleteQuery(table);
		query.addConditions(idField.eq(id));

		return Mono.from(query)
		        .then();
	}

	@SuppressWarnings("rawtypes")
	protected Field getField(String fieldName) { // NOSONAR
		// this return type has to be generic.
		return table.field(convertToJOOQFieldName(fieldName));
	}

	protected Condition filter(AbstractCondition condition) {

		if (condition == null)
			return DSL.noCondition();

		Condition cond = null;
		if (condition instanceof ComplexCondition cc)
			cond = complexConditionFilter(cc);
		else
			cond = filterConditionFilter((FilterCondition) condition);

		if (cond == null)
			return DSL.noCondition();

		return condition.isNegate() ? cond.not() : cond;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private Condition filterConditionFilter(FilterCondition fc) { // NOSONAR
		// Just 16 beyond the limit.

		Field field = this.getField(fc.getField()); // NOSONAR
		// Field has to be a raw type because we are generalising

		if (field == null || field.getDataType() == null)
			return DSL.noCondition();

		switch (fc.getOperator()) {
		case BETWEEN:
			return field
			        .between(fc.isValueField() ? (Field<?>) this.getField(fc.getValue())
			                : this.fieldValue(field, fc.getValue()))
			        .and(fc.isToValueField() ? (Field<?>) this.getField(fc.getToValue())
			                : this.fieldValue(field, fc.getToValue()));

		case EQUALS:
			return field.eq(fc.isValueField() ? (Field<?>) this.getField(fc.getValue())
			        : this.fieldValue(field, fc.getValue()));

		case GREATER_THAN:
			return field.gt(fc.isValueField() ? (Field<?>) this.getField(fc.getValue())
			        : this.fieldValue(field, fc.getValue()));

		case GREATER_THAN_EQUAL:
			return field.ge(fc.isValueField() ? (Field<?>) this.getField(fc.getValue())
			        : this.fieldValue(field, fc.getValue()));

		case LESS_THAN:
			return field.lt(fc.isValueField() ? (Field<?>) this.getField(fc.getValue())
			        : this.fieldValue(field, fc.getValue()));

		case LESS_THAN_EQUAL:
			return field.le(fc.isValueField() ? (Field<?>) this.getField(fc.getValue())
			        : this.fieldValue(field, fc.getValue()));

		case IS_FALSE:
			return field.isFalse();

		case IS_TRUE:
			return field.isTrue();

		case IS_NULL:
			return field.isNull();

		case IN:
			return field.in(this.multiFieldValue(field, fc.getValue()));

		case LIKE:
			return field.like(fc.getValue());

		case STRING_LOOSE_EQUAL:
			return field.like("%" + fc.getValue() + "%");

		default:
			return null;
		}
	}

	private List<Object> multiFieldValue(Field<?> field, String value) {

		if (value == null || value.isBlank())
			return List.of();

		int from = 0;
		String iValue = value.trim();

		List<Object> obj = new ArrayList<>();
		for (int i = 0; i < iValue.length(); i++) { // NOSONAR
			// Having multiple continue statements is not confusing

			if (iValue.charAt(i) != ',')
				continue;

			if (i != 0 && iValue.charAt(i - 1) == '\\')
				continue;

			String str = iValue.substring(from, i)
			        .trim();
			if (str.isEmpty())
				continue;

			obj.add(this.fieldValue(field, str));
			from = i + 1;
		}

		return obj;

	}

	private Object fieldValue(Field<?> field, String value) {

		DataType<?> dt = field.getDataType();

		if (dt.isString() || dt.isJSON() || dt.isEnum())
			return value;

		if (dt.isNumeric()) {

			if (dt.hasPrecision())
				return Double.valueOf(value);

			return Long.valueOf(value);
		}

		if (dt.isDate() || dt.isDateTime() || dt.isTime() || dt.isTimestamp()) {

			return value.equals("now") ? LocalDateTime.now()
			        : LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.valueOf(value)), ZoneId.of("UTC"));
		}

		return value;
	}

	private Condition complexConditionFilter(ComplexCondition cc) {

		if (cc.getConditions() == null || cc.getConditions()
		        .isEmpty())
			return DSL.noCondition();

		List<Condition> conds = cc.getConditions()
		        .stream()
		        .map(this::filter)
		        .toList();
		return cc.getOperator() == ComplexConditionOperator.AND ? DSL.and(conds) : DSL.or(conds);
	}

	protected Mono<R> getRecordById(I id) {

		return Mono.from(dslContext.selectFrom(table)
		        .where(idField.eq(id)))
		        .switchIfEmpty(Mono.defer(
		                () -> messageResourceService.getMessage(OBJECT_NOT_FOUND, this.pojoClass.getSimpleName(), id)
		                        .map(msg ->
								{
			                        throw new GenericException(HttpStatus.NOT_FOUND, msg);
		                        })));
	}

	protected Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> getSelectJointStep() {
		return Tuples.of(dslContext.select(Arrays.asList(table.fields()))
		        .from(table),
		        dslContext.select(DSL.count())
		                .from(table));
	}

	private D rowMapper(Row row, RowMetadata meta) {
		Record rec = this.table.newRecord();

		rec.fromMap(meta.getColumnMetadatas()
		        .stream()
		        .filter(e -> row.get(e.getName()) != null)
		        .map(e -> Tuples.of(e.getName(), row.get(e.getName())))
		        .collect(Collectors.toMap(Tuple2::getT1, Tuple2::getT2)));

		return rec.into(this.pojoClass);
	}
}
