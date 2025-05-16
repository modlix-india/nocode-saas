package com.fincity.saas.entity.processor.dao.base;

import com.fincity.saas.entity.processor.dto.base.BaseValueDto;
import com.fincity.saas.entity.processor.enums.Platform;
import com.fincity.saas.entity.processor.model.common.BaseValue;
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
    private static final String PLATFORM = "PLATFORM";
    private static final String ORDER = "ORDER";

    protected final Field<ULong> valueTemplateIdField;
    protected final Field<Boolean> isParentField;
    protected final Field<Platform> platformField;
    protected final Field<Integer> orderField;

    protected BaseValueDAO(Class<D> flowPojoClass, Table<R> flowTable, Field<ULong> flowTableId) {
        super(flowPojoClass, flowTable, flowTableId);
        this.valueTemplateIdField = flowTable.field(VALUE_TEMPLATE_ID, ULong.class);
        this.isParentField = flowTable.field(IS_PARENT, Boolean.class);
        this.platformField = flowTable.field(PLATFORM, Platform.class);
        this.orderField = flowTable.field(ORDER, Integer.class);
    }

    public Mono<Boolean> existsById(
            String appCode, String clientCode, Platform platform, ULong valueTemplateId, ULong... valueEntityIds) {

        if (valueEntityIds == null || valueEntityIds.length == 0) return Mono.just(Boolean.FALSE);

        return Mono.from(this.dslContext
                        .selectOne()
                        .from(this.table)
                        .where(DSL.and(this.getBaseValueConditions(
                                appCode, clientCode, platform, valueTemplateId, null, valueEntityIds))))
                .map(rec -> Boolean.TRUE)
                .defaultIfEmpty(Boolean.FALSE);
    }

    public Mono<Boolean> existsByName(
            String appCode, String clientCode, Platform platform, ULong valueTemplateId, String... valueEntityNames) {

        if (valueEntityNames == null || valueEntityNames.length == 0) return Mono.just(Boolean.FALSE);

        return Mono.from(this.dslContext
                        .selectOne()
                        .from(this.table)
                        .where(DSL.and(this.getBaseValueNameConditions(
                                appCode, clientCode, platform, valueTemplateId, null, valueEntityNames))))
                .map(rec -> Boolean.TRUE)
                .defaultIfEmpty(Boolean.FALSE);
    }

    public Mono<List<D>> getAllValues(
            String appCode, String clientCode, Platform platform, ULong valueTemplateId, Boolean isParent) {
        return Flux.from(this.dslContext
                        .selectFrom(this.table)
                        .where(DSL.and(
                                this.getBaseValueConditions(appCode, clientCode, platform, valueTemplateId, isParent))))
                .map(e -> e.into(super.pojoClass))
                .collectList();
    }

    public Mono<List<BaseValue>> getAllValueTemplateIdAndNames(
            String appCode, String clientCode, Platform platform, ULong valueTemplateId, Boolean isParent) {
        return Flux.from(this.dslContext
                        .select(this.idField, super.nameField)
                        .from(this.table)
                        .where(DSL.and(
                                this.getBaseValueConditions(appCode, clientCode, platform, valueTemplateId, isParent))))
                .map(e -> BaseValue.of(e.get(this.idField), e.get(super.nameField), e.get(this.orderField)))
                .collectList();
    }

    public Mono<List<BaseValue>> getAllValueTemplateIdAndNames(
            String appCode, String clientCode, Platform platform, ULong valueTemplateId) {
        return Flux.from(this.dslContext
                        .select(this.idField, super.nameField)
                        .from(this.table)
                        .where(DSL.and(
                                this.getBaseValueConditions(appCode, clientCode, platform, valueTemplateId, null))))
                .map(e -> BaseValue.of(e.get(this.idField), e.get(super.nameField), e.get(this.orderField)))
                .collectList();
    }

    protected List<Condition> getBaseCommonConditions(
            String appCode, String clientCode, Platform platform, ULong valueTemplateId, Boolean onlyParent) {
        List<Condition> conditions = new ArrayList<>();
        conditions.add(super.appCodeField.eq(appCode));
        conditions.add(super.clientCodeField.eq(clientCode));
        conditions.add(this.valueTemplateIdField.eq(valueTemplateId));
        if (platform != null) conditions.add(this.platformField.eq(platform));
        if (onlyParent != null) conditions.add(this.isParentField.eq(onlyParent));
        conditions.add(super.isActiveTrue()); // we always need Active value entities
        return conditions;
    }

    protected List<Condition> getBaseValueConditions(
            String appCode,
            String clientCode,
            Platform platform,
            ULong valueTemplateId,
            Boolean onlyParent,
            ULong... valueEntityIds) {
        List<Condition> conditions =
                this.getBaseCommonConditions(appCode, clientCode, platform, valueTemplateId, onlyParent);
        if (valueEntityIds.length > 0)
            conditions.add(super.idField.in(
                    Arrays.stream(valueEntityIds).filter(Objects::nonNull).toArray(ULong[]::new)));
        return conditions;
    }

    protected List<Condition> getBaseValueNameConditions(
            String appCode,
            String clientCode,
            Platform platform,
            ULong valueTemplateId,
            Boolean onlyParent,
            String... valeEntityNames) {
        List<Condition> conditions =
                this.getBaseCommonConditions(appCode, clientCode, platform, valueTemplateId, onlyParent);
        if (valeEntityNames.length > 0)
            conditions.add(super.nameField.in(
                    Arrays.stream(valeEntityNames).filter(Objects::nonNull).toArray(String[]::new)));
        return conditions;
    }
}
