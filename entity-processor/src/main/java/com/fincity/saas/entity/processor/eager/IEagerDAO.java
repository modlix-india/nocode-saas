package com.fincity.saas.entity.processor.eager;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.entity.processor.eager.relations.RecordEnrichmentService;
import com.fincity.saas.entity.processor.eager.relations.resolvers.RelationResolver;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.SetValuedMap;
import org.jooq.Condition;
import org.jooq.DSLContext;
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
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public interface IEagerDAO<R extends UpdatableRecord<R>> {

    DSLContext getDslContext();

    Table<R> getTable();

    Map<String, Tuple2<Table<?>, String>> getRelationMap();

    SetValuedMap<Class<? extends RelationResolver>, String> getRelationResolverMap();

    default RecordEnrichmentService getRecordEnrichmentService() {
        return null;
    }

    @SuppressWarnings("rawtypes")
    Field getField(String fieldName, SelectJoinStep<Record> selectJoinStep);

    Mono<Condition> filter(AbstractCondition condition, SelectJoinStep<Record> selectJoinStep);

    default Mono<Map<String, Object>> readSingleRecordByIdentityEager(
            AbstractCondition condition, List<String> tableFields, MultiValueMap<String, String> queryParams) {
        return getSelectJointStepEager(tableFields, queryParams).flatMap(tuple -> {
            Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> selectJoinStepTuple = tuple.getT1();
            Map<String, Tuple2<Table<?>, String>> relations = tuple.getT2();

            return this.filter(condition, selectJoinStepTuple.getT1()).flatMap(filterCondition -> Mono.from(
                            selectJoinStepTuple.getT1().where(filterCondition))
                    .map(rec -> this.processRelatedData(rec.intoMap(), relations))
                    .flatMap(rec -> this.getRecordEnrichmentService() != null
                            ? this.getRecordEnrichmentService().enrich(rec, this.getRelationResolverMap(), queryParams)
                            : Mono.just(rec)));
        });
    }

    @SuppressWarnings("unchecked")
    default Mono<Page<Map<String, Object>>> readPageFilterEager(
            Pageable pageable,
            AbstractCondition condition,
            List<String> tableFields,
            MultiValueMap<String, String> queryParams) {
        return this.getSelectJointStepEager(tableFields, queryParams).flatMap(tuple -> {
            Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> selectJoinStepTuple = tuple.getT1();
            Map<String, Tuple2<Table<?>, String>> relations = tuple.getT2();

            return this.filter(condition, selectJoinStepTuple.getT1()).flatMap(filterCondition -> {
                Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> filteredQueries = selectJoinStepTuple
                        .mapT1(e -> (SelectJoinStep<Record>) e.where(filterCondition))
                        .mapT2(e -> (SelectJoinStep<Record1<Integer>>) e.where(filterCondition));

                return this.listAsMap(pageable, filteredQueries, relations, queryParams);
            });
        });
    }

    default List<Field<?>> getMainTableBaseFields(List<String> tableFields, MultiValueMap<String, String> queryParams) {
        return this.getTableFields(tableFields, this.getTable());
    }

    default SelectJoinStep<Record> applyBaseTableJoins(
            SelectJoinStep<Record> query, MultiValueMap<String, String> queryParams) {
        return query;
    }

    default SelectJoinStep<Record1<Integer>> applyCountBaseTableJoins(
            SelectJoinStep<Record1<Integer>> query, MultiValueMap<String, String> queryParams) {
        return query;
    }

    default Mono<
                    Tuple2<
                            Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>>,
                            Map<String, Tuple2<Table<?>, String>>>>
            getSelectJointStepEager(List<String> tableFields, MultiValueMap<String, String> queryParams) {

        DSLContext dslContext = this.getDslContext();
        Table<?> mainTable = this.getTable();

        Map<String, Tuple2<Table<?>, String>> relations = new HashMap<>(this.getRelationMap());

        if (tableFields != null && !tableFields.isEmpty())
            relations.keySet().removeIf(key -> !tableFields.contains(key));

        Boolean eager = EagerUtil.getIsEagerParams(queryParams);
        List<String> eagerFields = EagerUtil.getEagerParams(queryParams);

        List<Field<?>> baseFields = this.getMainTableBaseFields(tableFields, queryParams);

        Map<String, Field<?>> baseFieldMap =
                baseFields.stream().collect(Collectors.toMap(Field::getName, f -> f, (a, b) -> b, LinkedHashMap::new));

        List<Field<?>> fields = this.getEagerFields(baseFields, eager, eagerFields, relations);

        SelectJoinStep<Record> recordQuery =
                this.applyBaseTableJoins(dslContext.select(fields).from(mainTable), queryParams);

        SelectJoinStep<Record1<Integer>> countQuery =
                this.applyCountBaseTableJoins(dslContext.select(DSL.count()).from(mainTable), queryParams);

        if (Boolean.FALSE.equals(eager) || relations.isEmpty())
            return Mono.just(Tuples.of(Tuples.of(recordQuery, countQuery), Map.of()));

        for (Map.Entry<String, Tuple2<Table<?>, String>> entry : relations.entrySet()) {

            String relationKey = entry.getKey();
            Table<?> relatedTable = entry.getValue().getT1();
            String tableAlias = entry.getValue().getT2();

            Table<?> aliasedTable = relatedTable.as(tableAlias);

            Field<ULong> fieldInMainTable = (Field<ULong>) baseFieldMap.get(EagerUtil.toJooqField(relationKey));
            Field<ULong> idFieldInRelatedTable = aliasedTable.field("ID", ULong.class);

            if (fieldInMainTable == null || idFieldInRelatedTable == null) continue;

            Condition joinCondition = fieldInMainTable.eq(idFieldInRelatedTable);
            recordQuery = recordQuery.leftJoin(aliasedTable).on(joinCondition);
            countQuery = countQuery.leftJoin(aliasedTable).on(joinCondition);
        }

        return Mono.just(Tuples.of(Tuples.of(recordQuery, countQuery), relations));
    }

    default Map<String, Object> processRelatedData(
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

    private List<Field<?>> getEagerFields(
            List<Field<?>> baseFields,
            Boolean eager,
            List<String> eagerFields,
            Map<String, Tuple2<Table<?>, String>> relations) {

        List<Field<?>> fields = new ArrayList<>(baseFields);

        if (Boolean.FALSE.equals(eager)) return fields;

        if (relations == null || relations.isEmpty()) return fields;

        Set<String> eagerFieldSet = (Boolean.TRUE.equals(eager) && (eagerFields == null || eagerFields.isEmpty()))
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
        return EagerDAOCache.getTableAliasedFieldsCache(Tuples.of(table, tableAlias), tuple -> {
            Table<?> aliasedTable = tuple.getT1().as(tuple.getT2());

            return Arrays.stream(aliasedTable.fields())
                    .collect(Collectors.toMap(
                            field -> tuple.getT2() + "." + field.getName(),
                            field -> field.as(tuple.getT2() + "." + field.getName()),
                            (a, b) -> b,
                            LinkedHashMap::new));
        });
    }

    private List<Field<?>> getTableFields(List<String> fields, Table<?> mainTable) {
        List<Field<?>> cachedFields =
                EagerDAOCache.getTableFieldsCache(mainTable, table -> Arrays.asList(table.fields()));

        if (fields == null || fields.isEmpty()) return new ArrayList<>(cachedFields);

        Set<String> fieldSet =
                fields.stream().map(EagerUtil::toJooqField).collect(Collectors.toCollection(LinkedHashSet::new));

        return cachedFields.stream()
                .filter(field -> fieldSet.contains(field.getName()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private Mono<Page<Map<String, Object>>> listAsMap(
            Pageable pageable,
            Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> selectJoinStepTuple,
            Map<String, Tuple2<Table<?>, String>> relations,
            MultiValueMap<String, String> queryParams) {

        List<SortField<?>> orderBy = new ArrayList<>();

        pageable.getSort().forEach(order -> {
            Field<?> field = this.getField(order.getProperty(), selectJoinStepTuple.getT1());
            if (field != null)
                orderBy.add(field.sort(order.getDirection() == Sort.Direction.ASC ? SortOrder.ASC : SortOrder.DESC));
        });

        Mono<Integer> recsCount = Mono.from(selectJoinStepTuple.getT2()).map(Record1::value1);

        SelectJoinStep<Record> baseQuery = selectJoinStepTuple.getT1();
        SelectLimitStep<Record> finalQuery = orderBy.isEmpty() ? baseQuery : baseQuery.orderBy(orderBy);

        Mono<List<Map<String, Object>>> recsList = Flux.from(
                        finalQuery.limit(pageable.getPageSize()).offset(pageable.getOffset()))
                .map(rec -> this.processRelatedData(rec.intoMap(), relations))
                .collectList()
                .flatMap(recs -> this.getRecordEnrichmentService() != null
                        ? this.getRecordEnrichmentService().enrich(recs, this.getRelationResolverMap(), queryParams)
                        : Mono.just(recs));

        return Mono.zip(recsList, recsCount)
                .map(tuple -> PageableExecutionUtils.getPage(tuple.getT1(), pageable, tuple::getT2));
    }
}
