package com.fincity.saas.entity.processor.dao.base;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.flow.dao.AbstractFlowUpdatableDAO;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.entity.processor.dto.base.BaseDto;
import com.fincity.saas.entity.processor.util.EagerUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public abstract class BaseDAO<R extends UpdatableRecord<R>, D extends BaseDto<D>>
        extends AbstractFlowUpdatableDAO<R, ULong, D> {

    private static final String CODE = "CODE";
    private static final String NAME = "NAME";
    private static final String TEMP_ACTIVE = "TEMP_ACTIVE";
    private static final String IS_ACTIVE = "IS_ACTIVE";

    protected final Field<String> codeField;
    protected final Field<String> nameField;
    protected final Field<Boolean> tempActiveField;
    protected final Field<Boolean> isActiveField;

    protected BaseDAO(Class<D> flowPojoClass, Table<R> flowTable, Field<ULong> flowTableId) {
        super(flowPojoClass, flowTable, flowTableId);
        this.codeField = flowTable.field(CODE, String.class);
        this.nameField = flowTable.field(NAME, String.class);
        this.tempActiveField = flowTable.field(TEMP_ACTIVE, Boolean.class);
        this.isActiveField = flowTable.field(IS_ACTIVE, Boolean.class);
    }

    public Mono<D> readByIdAndAppCodeAndClientCode(ULong id, String appCode, String clientCode) {
        return this.getSelectJointStep()
                .map(Tuple2::getT1)
                .flatMap(e -> Mono.from(
                        e.where(idField.eq(id).and(appCodeField.eq(appCode)).and(clientCodeField.eq(clientCode)))))
                .switchIfEmpty(Mono.defer(() -> messageResourceService
                        .getMessage(AbstractMessageService.OBJECT_NOT_FOUND, this.pojoClass.getSimpleName(), id)
                        .map(msg -> {
                            throw new GenericException(HttpStatus.NOT_FOUND, msg);
                        })))
                .map(e -> e.into(this.pojoClass));
    }

    @SuppressWarnings("unchecked")
    public Mono<Page<Map<String, Object>>> readPageFilterEager(Pageable pageable, AbstractCondition condition) {
        return getSelectJointStepEager().flatMap(tuple -> {
            Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> selectJoinStepTuple = tuple.getT1();
            Map<String, Table<?>> relations = tuple.getT2();

            return filter(condition).flatMap(filterCondition -> {
                Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> filteredQueries = selectJoinStepTuple
                        .mapT1(e -> (SelectJoinStep<Record>) e.where(filterCondition))
                        .mapT2(e -> (SelectJoinStep<Record1<Integer>>) e.where(filterCondition));

                return listAsMap(pageable, filteredQueries, relations);
            });
        });
    }

    protected Mono<Tuple2<Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>>, Map<String, Table<?>>>> getSelectJointStepEager() {
        Map<String, Table<?>> relations = EagerUtil.getRelationMap(this.pojoClass);

        List<Field<?>> fields = relations == null || relations.isEmpty()
                ? Arrays.asList(this.table.fields())
                : this.getEagerFields(null, this.table, relations.values());

        SelectJoinStep<Record> recordQuery = dslContext.select(fields).from(table);
        SelectJoinStep<Record1<Integer>> countQuery =
                dslContext.select(DSL.count()).from(table);

        if (relations != null && !relations.isEmpty()) {
            for (Map.Entry<String, Table<?>> entry : relations.entrySet()) {
                Table<?> relatedTable = entry.getValue();
                if (relatedTable != null) {
                    Field<ULong> fieldInMainTable = table.field(EagerUtil.toJooqField(entry.getKey()), ULong.class);
                    Field<ULong> idFieldInRelatedTable = relatedTable.field("ID", ULong.class);

                    if (fieldInMainTable != null && idFieldInRelatedTable != null) {
                        Condition joinCondition = fieldInMainTable.eq(idFieldInRelatedTable);

                        recordQuery = recordQuery.leftJoin(relatedTable).on(joinCondition);
                        countQuery = countQuery.leftJoin(relatedTable).on(joinCondition);
                    }
                }
            }
        }

        return Mono.just(Tuples.of(Tuples.of(recordQuery, countQuery), relations));
    }

    private List<Field<?>> getEagerFields(
            List<String> eagerFields, Table<?> mainTable, Collection<Table<?>> relationTables) {

        List<Field<?>> fields = new ArrayList<>(Arrays.asList(mainTable.fields()));

        if (eagerFields == null || eagerFields.isEmpty()) {
            for (Table<?> relationTable : relationTables) {
                String tableName = relationTable.getName();
                for (Field<?> field : relationTable.fields()) {
                    fields.add(field.as(tableName + "." + field.getName()));
                }
            }
        } else {
            Set<String> eagerFieldSet =
                    eagerFields.stream().map(EagerUtil::toJooqField).collect(Collectors.toSet());

            for (Table<?> relationTable : relationTables) {
                String tableName = relationTable.getName();
                for (Field<?> field : relationTable.fields()) {
                    String aliasedName = tableName + "." + field.getName();
                    if (eagerFieldSet.contains(aliasedName)) {
                        fields.add(field.as(aliasedName));
                    }
                }
            }
        }

        return fields;
    }

    private void processRelatedData(Map<String, Object> map, Map<String, Table<?>> relations) {
        Map<String, Object> camelCaseMap = EagerUtil.convertMapKeysToCamelCase(map);
        map.clear();
        map.putAll(camelCaseMap);

        Map<String, String> tableNameMap = HashMap.newHashMap(relations.size());
        relations.forEach((key, value) -> tableNameMap.put(
                key, EagerUtil.fromJooqField(value.getName())));

        Map<String, Map<String, Object>> relationGroups = HashMap.newHashMap(relations.size());

        List<String> keysToRemove = new ArrayList<>();

        for (Map.Entry<String, Object> mapEntry : map.entrySet()) {
            String key = mapEntry.getKey();
            Object value = mapEntry.getValue();

            for (Map.Entry<String, String> tableEntry : tableNameMap.entrySet()) {
                String relationFieldName = tableEntry.getKey();
                String tableName = tableEntry.getValue();

                if (key.startsWith(tableName + ".")) {
                    Map<String, Object> relatedEntityMap =
                            relationGroups.computeIfAbsent(relationFieldName, k -> new LinkedHashMap<>());

                    String nestedFieldName = key.substring(tableName.length() + 1);
                    relatedEntityMap.put(nestedFieldName, value);

                    keysToRemove.add(key);

                    break;
                }
            }
        }

        keysToRemove.forEach(map::remove);

        relationGroups.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .forEach(entry -> map.put(entry.getKey(), entry.getValue()));
    }

    private Mono<Page<Map<String, Object>>> listAsMap(
            Pageable pageable,
            Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> selectJoinStepTuple,
            Map<String, Table<?>> relations) {

        List<SortField<?>> orderBy = new ArrayList<>();

        pageable.getSort().forEach(order -> {
            Field<?> field = this.getField(order.getProperty());
            if (field != null)
                orderBy.add(field.sort(order.getDirection() == Sort.Direction.ASC ? SortOrder.ASC : SortOrder.DESC));
        });

        Mono<Integer> recordsCount = Mono.from(selectJoinStepTuple.getT2()).map(Record1::value1);

        SelectJoinStep<Record> baseQuery = selectJoinStepTuple.getT1();
        SelectLimitStep<Record> finalQuery = orderBy.isEmpty() ? baseQuery : baseQuery.orderBy(orderBy);

        boolean hasRelations = relations != null && !relations.isEmpty();

        Mono<List<Map<String, Object>>> recordsList = Flux.from(
                        finalQuery.limit(pageable.getPageSize()).offset(pageable.getOffset()))
                .map(rec -> {
                    Map<String, Object> map = rec.intoMap();
                    if (hasRelations)
                        processRelatedData(map, relations);
                    return map;
                })
                .collectList();

        return Mono.zip(recordsList, recordsCount)
                .map(tuple -> PageableExecutionUtils.getPage(tuple.getT1(), pageable, tuple::getT2));
    }

    public Mono<D> readInternal(ULong id) {
        return Mono.from(this.dslContext
                        .selectFrom(this.table)
                        .where(this.idField.eq(id))
                        .limit(1))
                .map(e -> e.into(this.pojoClass));
    }

    public Mono<D> readByCode(String code) {
        return Mono.from(this.dslContext.selectFrom(this.table).where(codeField.eq(code)))
                .map(result -> result.into(this.pojoClass));
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
