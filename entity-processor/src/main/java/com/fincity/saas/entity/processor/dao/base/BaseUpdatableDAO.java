package com.fincity.saas.entity.processor.dao.base;

import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.flow.dao.AbstractFlowUpdatableDAO;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.entity.processor.dto.base.BaseUpdatableDto;
import com.fincity.saas.entity.processor.util.EagerUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.jooq.Condition;
import org.jooq.DeleteQuery;
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
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public abstract class BaseUpdatableDAO<R extends UpdatableRecord<R>, D extends BaseUpdatableDto<D>>
        extends AbstractFlowUpdatableDAO<R, ULong, D> {

    private static final String CODE = "CODE";
    private static final String NAME = "NAME";
    private static final String TEMP_ACTIVE = "TEMP_ACTIVE";
    private static final String IS_ACTIVE = "IS_ACTIVE";

    private static final Map<Table<?>, List<Field<?>>> tableFieldsCache = new ConcurrentHashMap<>();
    private static final Map<Tuple2<Table<?>, String>, Map<String, Field<?>>> tableAliasedFieldsCache =
            new ConcurrentHashMap<>();

    protected final Field<String> codeField;
    protected final Field<String> nameField;
    protected final Field<Boolean> tempActiveField;
    protected final Field<Boolean> isActiveField;

    protected BaseUpdatableDAO(Class<D> flowPojoClass, Table<R> flowTable, Field<ULong> flowTableId) {
        super(flowPojoClass, flowTable, flowTableId);
        this.codeField = flowTable.field(CODE, String.class);
        this.nameField = flowTable.field(NAME, String.class);
        this.tempActiveField = flowTable.field(TEMP_ACTIVE, Boolean.class);
        this.isActiveField = flowTable.field(IS_ACTIVE, Boolean.class);
    }

    private <T, V> Mono<T> objectNotFoundError(V value) {
        return messageResourceService
                .getMessage(AbstractMessageService.OBJECT_NOT_FOUND, this.pojoClass.getSimpleName(), value)
                .handle((msg, sink) -> sink.error(new GenericException(HttpStatus.NOT_FOUND, msg)));
    }

    public Mono<D> readByIdAndAppCodeAndClientCode(ULong id, String appCode, String clientCode) {
        return this.readSingleRecordByIdentity(idField, id, appCode, clientCode);
    }

    public Mono<D> readByCodeAndAppCodeAndClientCode(String code, String appCode, String clientCode) {
        return this.readSingleRecordByIdentity(codeField, code, appCode, clientCode);
    }

    private <V> Mono<D> readSingleRecordByIdentity(
            Field<V> identityField, V identity, String appCode, String clientCode) {
        return this.getSelectJointStep()
                .map(Tuple2::getT1)
                .flatMap(select -> Mono.from(select.where(
                        identityField.eq(identity).and(appCodeField.eq(appCode)).and(clientCodeField.eq(clientCode)))))
                .switchIfEmpty(Mono.defer(() -> objectNotFoundError(identity)))
                .map(rec -> rec.into(this.pojoClass));
    }

    public Mono<Map<String, Object>> readByIdAndAppCodeAndClientCodeEager(
            ULong id,
            String appCode,
            String clientCode,
            List<String> tableFields,
            Boolean eager,
            List<String> eagerFields) {
        return this.readSingleRecordByIdentityEager(idField, id, appCode, clientCode, tableFields, eager, eagerFields);
    }

    public Mono<Map<String, Object>> readByCodeAndAppCodeAndClientCodeEager(
            String code,
            String appCode,
            String clientCode,
            List<String> tableFields,
            Boolean eager,
            List<String> eagerFields) {
        return this.readSingleRecordByIdentityEager(
                codeField, code, appCode, clientCode, tableFields, eager, eagerFields);
    }

    private <V> Mono<Map<String, Object>> readSingleRecordByIdentityEager(
            Field<V> identityField,
            V identity,
            String appCode,
            String clientCode,
            List<String> tableFields,
            Boolean eager,
            List<String> eagerFields) {

        return this.getSelectJointStepEager(tableFields, eager, eagerFields)
                .flatMap(tuple -> {
                    Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> selectJoinStepTuple =
                            tuple.getT1();
                    Map<String, Tuple2<Table<?>, String>> relations = tuple.getT2();

                    return Mono.from(selectJoinStepTuple
                                    .getT1()
                                    .where(identityField
                                            .eq(identity)
                                            .and(appCodeField.eq(appCode))
                                            .and(clientCodeField.eq(clientCode))))
                            .map(rec -> processRelatedData(rec.intoMap(), relations));
                })
                .switchIfEmpty(Mono.defer(() -> objectNotFoundError(identity)));
    }

    public Mono<D> readInternal(ULong id) {
        return Mono.from(this.dslContext.selectFrom(this.table).where(this.idField.eq(id)))
                .map(e -> e.into(this.pojoClass));
    }

    public Mono<D> readInternal(String code) {
        return Mono.from(this.dslContext.selectFrom(this.table).where(codeField.eq(code)))
                .map(result -> result.into(this.pojoClass));
    }

    @SuppressWarnings("unchecked")
    public Mono<Page<Map<String, Object>>> readPageFilterEager(
            Pageable pageable,
            AbstractCondition condition,
            List<String> tableFields,
            Boolean eager,
            List<String> eagerFields) {
        return getSelectJointStepEager(tableFields, eager, eagerFields).flatMap(tuple -> {
            Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> selectJoinStepTuple = tuple.getT1();
            Map<String, Tuple2<Table<?>, String>> relations = tuple.getT2();

            return filter(condition).flatMap(filterCondition -> {
                Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> filteredQueries = selectJoinStepTuple
                        .mapT1(e -> (SelectJoinStep<Record>) e.where(filterCondition))
                        .mapT2(e -> (SelectJoinStep<Record1<Integer>>) e.where(filterCondition));

                return this.listAsMap(pageable, filteredQueries, relations);
            });
        });
    }

    protected Mono<
                    Tuple2<
                            Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>>,
                            Map<String, Tuple2<Table<?>, String>>>>
            getSelectJointStepEager(List<String> tableFields, Boolean eager, List<String> eagerFields) {
        Map<String, Tuple2<Table<?>, String>> relations = EagerUtil.getRelationMap(this.pojoClass);

        List<Field<?>> fields = this.getEagerFields(tableFields, eager, eagerFields, this.table, relations);

        SelectJoinStep<Record> recordQuery = dslContext.select(fields).from(this.table);
        SelectJoinStep<Record1<Integer>> countQuery =
                dslContext.select(DSL.count()).from(this.table);

        if (Boolean.FALSE.equals(eager)) return Mono.just(Tuples.of(Tuples.of(recordQuery, countQuery), Map.of()));

        if ((relations == null || relations.isEmpty()))
            return Mono.just(Tuples.of(Tuples.of(recordQuery, countQuery), Map.of()));

        for (Map.Entry<String, Tuple2<Table<?>, String>> entry : relations.entrySet()) {
            String relationKey = entry.getKey();
            Table<?> relatedTable = entry.getValue().getT1();
            String tableAlias = entry.getValue().getT2();

            Table<?> aliasedTable = relatedTable.as(tableAlias);

            Field<ULong> fieldInMainTable = this.table.field(EagerUtil.toJooqField(relationKey), ULong.class);
            Field<ULong> idFieldInRelatedTable = aliasedTable.field("ID", ULong.class);

            if (fieldInMainTable != null && idFieldInRelatedTable != null) {
                Condition joinCondition = fieldInMainTable.eq(idFieldInRelatedTable);

                recordQuery = recordQuery.leftJoin(aliasedTable).on(joinCondition);
                countQuery = countQuery.leftJoin(aliasedTable).on(joinCondition);
            }
        }

        return Mono.just(Tuples.of(Tuples.of(recordQuery, countQuery), relations));
    }

    private List<Field<?>> getTableFields(List<String> fields, Table<?> mainTable) {
        List<Field<?>> cachedFields =
                tableFieldsCache.computeIfAbsent(mainTable, table -> Arrays.asList(table.fields()));

        if (fields == null || fields.isEmpty()) return new ArrayList<>(cachedFields);

        Set<String> fieldSet =
                fields.stream().map(EagerUtil::toJooqField).collect(Collectors.toCollection(LinkedHashSet::new));

        return cachedFields.stream()
                .filter(field -> fieldSet.contains(field.getName()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<Field<?>> getEagerFields(
            List<String> mainTableFields,
            Boolean eager,
            List<String> eagerFields,
            Table<?> mainTable,
            Map<String, Tuple2<Table<?>, String>> relations) {

        List<Field<?>> fields = this.getTableFields(mainTableFields, mainTable);

        if (Boolean.FALSE.equals(eager)) return fields;

        if (relations == null || relations.isEmpty()) return fields;

        Set<String> eagerFieldSet = eagerFields == null || eagerFields.isEmpty()
                ? null
                : eagerFields.stream().map(EagerUtil::toJooqField).collect(Collectors.toCollection(LinkedHashSet::new));

        for (Map.Entry<String, Tuple2<Table<?>, String>> entry : relations.entrySet()) {
            Table<?> relationTable = entry.getValue().getT1();
            String tableAlias = entry.getValue().getT2();
            Map<String, Field<?>> aliasedFields = this.getAliasedFields(relationTable, tableAlias);

            if (eagerFieldSet == null) {
                fields.addAll(aliasedFields.values());
            } else {
                eagerFieldSet.stream()
                        .map(eagerField -> tableAlias + "." + eagerField)
                        .filter(aliasedFields::containsKey)
                        .map(aliasedFields::get)
                        .forEach(fields::add);
            }
        }

        return fields;
    }

    private Map<String, Field<?>> getAliasedFields(Table<?> table, String tableAlias) {
        return tableAliasedFieldsCache.computeIfAbsent(Tuples.of(table, tableAlias), tuple -> {
            Map<String, Field<?>> map = new LinkedHashMap<>();
            Arrays.stream(tuple.getT1().fields()).forEach(field -> {
                String fieldName = field.getName();
                String aliasedFieldName = tuple.getT2() + "." + fieldName;
                map.put(
                        aliasedFieldName,
                        DSL.field(DSL.name(tuple.getT2(), fieldName)).as(aliasedFieldName));
            });
            return map;
        });
    }

    private Map<String, Object> processRelatedData(
            Map<String, Object> recordMap, Map<String, Tuple2<Table<?>, String>> relations) {
        Set<String> validAliases =
                relations.values().stream().map(Tuple2::getT2).collect(Collectors.toSet());

        Map<String, Object> convertedMap = LinkedHashMap.newLinkedHashMap(recordMap.size());
        Map<String, Map<String, Object>> relationGroups = LinkedHashMap.newLinkedHashMap(relations.size());

        for (Map.Entry<String, Object> entry : recordMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            int dotIndex = key.indexOf('.');
            if (dotIndex > 0 && value != null) {
                String alias = key.substring(0, dotIndex);
                if (validAliases.contains(alias)) {
                    String nestedField = EagerUtil.fromJooqField(key.substring(dotIndex + 1));
                    relationGroups
                            .computeIfAbsent(alias, k -> new LinkedHashMap<>())
                            .put(nestedField, value);
                    continue;
                }
            }

            if (value != null) convertedMap.put(EagerUtil.fromJooqField(key), value);
        }

        relationGroups.entrySet().stream()
                .filter(groupEntry -> !groupEntry.getValue().isEmpty())
                .forEach(groupEntry ->
                        convertedMap.put(EagerUtil.fromJooqField(groupEntry.getKey()), groupEntry.getValue()));

        return convertedMap;
    }

    private Mono<Page<Map<String, Object>>> listAsMap(
            Pageable pageable,
            Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> selectJoinStepTuple,
            Map<String, Tuple2<Table<?>, String>> relations) {

        List<SortField<?>> orderBy = new ArrayList<>();

        pageable.getSort().forEach(order -> {
            Field<?> field = this.getField(order.getProperty());
            if (field != null)
                orderBy.add(field.sort(order.getDirection() == Sort.Direction.ASC ? SortOrder.ASC : SortOrder.DESC));
        });

        Mono<Integer> recordsCount = Mono.from(selectJoinStepTuple.getT2()).map(Record1::value1);

        SelectJoinStep<Record> baseQuery = selectJoinStepTuple.getT1();
        SelectLimitStep<Record> finalQuery = orderBy.isEmpty() ? baseQuery : baseQuery.orderBy(orderBy);

        Mono<List<Map<String, Object>>> recordsList = Flux.from(
                        finalQuery.limit(pageable.getPageSize()).offset(pageable.getOffset()))
                .map(rec -> processRelatedData(rec.intoMap(), relations))
                .collectList();

        return Mono.zip(recordsList, recordsCount)
                .map(tuple -> PageableExecutionUtils.getPage(tuple.getT1(), pageable, tuple::getT2));
    }

    public Mono<Integer> deleteByCode(String code) {

        DeleteQuery<R> query = dslContext.deleteQuery(table);
        query.addConditions(codeField.eq(code));

        return Mono.from(query);
    }

    public Mono<Integer> deleteMultiple(List<ULong> ids) {
        DeleteQuery<R> query = dslContext.deleteQuery(table);
        query.addConditions(this.idField.in(ids));
        return Mono.from(query);
    }

    public Mono<Integer> deleteMultiple(Flux<ULong> ids) {
        return ids.collectList().flatMap(this::deleteMultiple);
    }

    protected Condition isActiveTrue() {
        return isActiveField.eq(Boolean.TRUE);
    }

    protected Condition isActiveFalse() {
        return isActiveField.eq(Boolean.FALSE);
    }

    protected Condition isActive(Boolean isActive) {
        if (isActive == null) return DSL.trueCondition();
        return isActiveField.eq(isActive);
    }

    protected Condition isActiveWithFalse(Boolean isActive) {
        if (isActive == null) return DSL.falseCondition();
        return isActiveField.eq(isActive);
    }
}
