package com.fincity.saas.entity.processor.dao.base;

import com.fincity.saas.entity.processor.dto.base.BaseValueDto;
import com.fincity.saas.entity.processor.model.common.IdAndValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class BaseValueDAO<R extends UpdatableRecord<R>, D extends BaseValueDto<D>> extends BaseDAO<R, D> {

    private static final String VALUE_TEMPLATE_ID = "VALUE_TEMPLATE_ID";
    private static final String IS_PARENT = "IS_PARENT";

    protected final Field<ULong> valueTemplateIdField;
    protected final Field<Boolean> isParentField;

    protected BaseValueDAO(Class<D> flowPojoClass, Table<R> flowTable, Field<ULong> flowTableId) {
        super(flowPojoClass, flowTable, flowTableId);
        this.valueTemplateIdField = flowTable.field(VALUE_TEMPLATE_ID, ULong.class);
        this.isParentField = flowTable.field(IS_PARENT, Boolean.class);
    }

    public Mono<Boolean> existsById(String appCode, String clientCode, ULong valueTemplateId, ULong... valueEntityIds) {
        return Mono.just(this.dslContext.fetchExists(this.dslContext
                .selectFrom(this.table)
                .where(DSL.and(
                        this.getBaseValueConditions(appCode, clientCode, valueTemplateId, null, valueEntityIds)))));
    }

    public Mono<List<D>> getAllValues(String appCode, String clientCode, ULong valueTemplateId, Boolean isParent) {
        return Flux.from(this.dslContext
                        .selectFrom(this.table)
                        .where(DSL.and(this.getBaseValueConditions(appCode, clientCode, valueTemplateId, isParent))))
                .map(e -> e.into(super.pojoClass))
                .collectList();
    }

    public Mono<List<IdAndValue<Integer, String>>> getAllValueTemplateIdAndNames(
            String appCode, String clientCode, ULong valueTemplateId, Boolean isParent) {
        return Flux.from(this.dslContext
                        .select(this.idField, super.nameField)
                        .from(this.table)
                        .where(DSL.and(this.getBaseValueConditions(appCode, clientCode, valueTemplateId, isParent))))
                .map(e -> new IdAndValue<>(e.get(this.idField).intValue(), e.get(super.nameField)))
                .collectList();
    }

    public Mono<List<IdAndValue<Integer, String>>> getAllValueTemplateIdAndNames(
            String appCode, String clientCode, ULong valueTemplateId) {
        return Flux.from(this.dslContext
                        .select(this.idField, super.nameField)
                        .from(this.table)
                        .where(DSL.and(this.getBaseValueConditions(appCode, clientCode, valueTemplateId, null))))
                .map(e -> new IdAndValue<>(e.get(this.idField).intValue(), e.get(super.nameField)))
                .collectList();
    }

    private List<Condition> getBaseValueConditions(
            String appCode, String clientCode, ULong valueTemplateId, Boolean onlyParent, ULong... valueEntityIds) {

        List<Condition> conditions = new ArrayList<>();

        conditions.add(this.appCodeField.eq(appCode));
        conditions.add(this.clientCodeField.eq(clientCode));
        conditions.add(this.valueTemplateIdField.eq(valueTemplateId));

        if (onlyParent != null) conditions.add(this.isParentField.eq(onlyParent));

        if (valueEntityIds.length > 0)
            conditions.add(this.idField.in(
                    Arrays.stream(valueEntityIds).filter(Objects::nonNull).toArray(ULong[]::new)));

        // we always need Active value entities
        conditions.add(super.isActiveTrue());

        return conditions;
    }
}
